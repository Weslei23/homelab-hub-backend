package com.homelab.homepage.domain.status.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.homelab.homepage.support.FakeHttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DiscordNotifierTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SERVICE_URL = "http://192.168.3.183:8096";

    private FakeHttpServer discord;
    private final AtomicInteger responseCode = new AtomicInteger( 204 );

    @BeforeEach
    void setUp()
    {
        discord = new FakeHttpServer()
                .handle( "/hook", exchange -> FakeHttpServer.reply( exchange, responseCode.get() ) );
    }

    @AfterEach
    void tearDown()
    {
        discord.close();
    }

    private DiscordNotifier notifier()
    {
        return new DiscordNotifier( discord.url( "/hook" ), "Homelab Hub", JSON );
    }

    /** Espera o envio assincrono e devolve o primeiro embed do payload. */
    private JsonNode awaitEmbed()
    {
        FakeHttpServer.await( () -> !discord.received( "/hook" ).isEmpty() );
        return awaitPayload().get( "embeds" ).get( 0 );
    }

    private JsonNode awaitPayload()
    {
        FakeHttpServer.await( () -> !discord.received( "/hook" ).isEmpty() );
        return JSON.readTree( discord.received( "/hook" ).getFirst().body() );
    }

    @Nested
    class Desativado
    {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource( strings = { "   " } )
        void semWebhookFicaDesativado( String webhookUrl )
        {
            assertThat( new DiscordNotifier( webhookUrl, "x", JSON ).isEnabled() ).isFalse();
        }

        @Test
        void comWebhookFicaAtivado()
        {
            assertThat( notifier().isEnabled() ).isTrue();
        }

        @Test
        void desativadoNaoEnviaNada() throws InterruptedException
        {
            DiscordNotifier notifier = new DiscordNotifier( "", "x", JSON );

            assertThatNoException().isThrownBy( () -> {
                notifier.notifyDown( "Jellyfin", SERVICE_URL, 502 );
                notifier.notifyUp( "Jellyfin", SERVICE_URL, 10, Duration.ofMinutes( 1 ) );
            } );
            assertThat( notifier.sendTest() ).isFalse();
            Thread.sleep( 200 );
            assertThat( discord.received() ).isEmpty();
        }
    }

    @Nested
    class Payload
    {
        @Test
        void enviaPostComJson()
        {
            notifier().notifyDown( "Jellyfin", SERVICE_URL, 502 );

            awaitPayload();
            assertThat( discord.received( "/hook" ).getFirst().method() ).isEqualTo( "POST" );
        }

        @Test
        void usaONomeConfigurado()
        {
            new DiscordNotifier( discord.url( "/hook" ), "Meu Lab", JSON ).notifyDown( "Jellyfin", SERVICE_URL, 502 );

            assertThat( awaitPayload().get( "username" ).asString() ).isEqualTo( "Meu Lab" );
        }

        @Test
        void avisoDeQueda()
        {
            notifier().notifyDown( "Jellyfin", SERVICE_URL, 502 );

            JsonNode embed = awaitEmbed();
            assertThat( embed.get( "title" ).asString() ).isEqualTo( "🔴 Jellyfin está offline" );
            assertThat( embed.get( "url" ).asString() ).isEqualTo( SERVICE_URL );
            assertThat( embed.get( "description" ).asString() ).isEqualTo( SERVICE_URL );
            assertThat( embed.get( "color" ).asInt() ).isEqualTo( 0xEF4444 );
            assertThat( embed.get( "fields" ).get( 0 ).get( "name" ).asString() ).isEqualTo( "Motivo" );
            assertThat( embed.get( "fields" ).get( 0 ).get( "value" ).asString() ).isEqualTo( "Respondeu HTTP 502" );
            assertThat( embed.get( "timestamp" ).asString() ).isNotBlank();
        }

        @Test
        void avisoDeQuedaSemResposta()
        {
            notifier().notifyDown( "Jellyfin", SERVICE_URL, null );

            assertThat( awaitEmbed().get( "fields" ).get( 0 ).get( "value" ).asString() )
                    .isEqualTo( "Sem resposta (timeout ou conexao recusada)" );
        }

        @Test
        void avisoDeRetorno()
        {
            notifier().notifyUp( "Jellyfin", SERVICE_URL, 42, Duration.ofMinutes( 6 ) );

            JsonNode embed = awaitEmbed();
            assertThat( embed.get( "title" ).asString() ).isEqualTo( "🟢 Jellyfin voltou" );
            assertThat( embed.get( "color" ).asInt() ).isEqualTo( 0x22C55E );
            assertThat( embed.get( "fields" ) ).hasSize( 2 );
            assertThat( embed.get( "fields" ).get( 0 ).get( "value" ).asString() ).isEqualTo( "42 ms" );
            assertThat( embed.get( "fields" ).get( 1 ).get( "name" ).asString() ).isEqualTo( "Ficou fora do ar por" );
            assertThat( embed.get( "fields" ).get( 1 ).get( "value" ).asString() ).isEqualTo( "6 min" );
        }

        @Test
        void avisoDeRetornoSemTempoForaDoAr()
        {
            notifier().notifyUp( "Jellyfin", SERVICE_URL, 42, null );

            assertThat( awaitEmbed().get( "fields" ) ).hasSize( 1 );
        }

        @ParameterizedTest
        @MethodSource( "com.homelab.homepage.domain.status.notify.DiscordNotifierTest#downtimes" )
        void formataOTempoForaDoAr( Duration downtime, String expected )
        {
            notifier().notifyUp( "Jellyfin", SERVICE_URL, 1, downtime );

            assertThat( awaitEmbed().get( "fields" ).get( 1 ).get( "value" ).asString() ).isEqualTo( expected );
        }

        @Test
        void urlQueNaoEHttpNaoViraLink()
        {
            notifier().notifyDown( "Jellyfin", "#", 502 );

            JsonNode embed = awaitEmbed();
            assertThat( embed.has( "url" ) ).isFalse();
            assertThat( embed.has( "description" ) ).isFalse();
        }

        @Test
        void naoQuebraComCaracteresEspeciais()
        {
            notifier().notifyDown( "Serviço \"Ñ\" \\ <teste>", SERVICE_URL, 502 );

            assertThat( awaitEmbed().get( "title" ).asString() ).isEqualTo( "🔴 Serviço \"Ñ\" \\ <teste> está offline" );
        }
    }

    @Nested
    class MusicasNovas
    {
        @Test
        void listaAsFaixasNovas()
        {
            notifier().notifyNewMusic( "Academia", "https://open.spotify.com/playlist/x",
                    java.util.List.of( "Get Lucky — Daft Punk", "Around the World — Daft Punk" ) );

            JsonNode embed = awaitEmbed();
            assertThat( embed.get( "title" ).asString() ).isEqualTo( "🎵 2 música(s) nova(s) em Academia" );
            assertThat( embed.get( "url" ).asString() ).isEqualTo( "https://open.spotify.com/playlist/x" );
            assertThat( embed.get( "color" ).asInt() ).isEqualTo( 0x8B5CF6 );
            assertThat( embed.get( "fields" ).get( 0 ).get( "value" ).asString() )
                    .isEqualTo( "• Get Lucky — Daft Punk\n• Around the World — Daft Punk" );
        }

        @Test
        void muitasFaixasMostraSoAsPrimeiras()
        {
            java.util.List<String> tracks = java.util.stream.IntStream.rangeClosed( 1, 25 ).mapToObj( i -> "Faixa " + i ).toList();

            notifier().notifyNewMusic( "Mix", "https://open.spotify.com/playlist/x", tracks );

            String value = awaitEmbed().get( "fields" ).get( 0 ).get( "value" ).asString();
            assertThat( value ).contains( "• Faixa 10" ).doesNotContain( "Faixa 11\n" ).endsWith( "+ 15 outras" );
            assertThat( value.lines() ).hasSize( DiscordNotifier.MAX_LISTED_TRACKS + 1 );
        }

        @Test
        void campoNuncaPassaDoLimiteDoDiscord()
        {
            java.util.List<String> tracks = java.util.stream.IntStream.rangeClosed( 1, 10 ).mapToObj( i -> "x".repeat( 200 ) ).toList();

            notifier().notifyNewMusic( "Mix", "https://open.spotify.com/playlist/x", tracks );

            String value = awaitEmbed().get( "fields" ).get( 0 ).get( "value" ).asString();
            assertThat( value ).hasSize( 1024 ).endsWith( "..." );
        }
    }

    static Stream<Arguments> downtimes()
    {
        return Stream.of(
                Arguments.of( Duration.ZERO, "0 s" ),
                Arguments.of( Duration.ofSeconds( 45 ), "45 s" ),
                Arguments.of( Duration.ofSeconds( 60 ), "1 min" ),
                Arguments.of( Duration.ofMinutes( 59 ).plusSeconds( 59 ), "59 min" ),
                Arguments.of( Duration.ofHours( 1 ), "1 h 0 min" ),
                Arguments.of( Duration.ofHours( 2 ).plusMinutes( 5 ), "2 h 5 min" ),
                Arguments.of( Duration.ofHours( 26 ), "26 h 0 min" ) );
    }

    @Nested
    class MensagemDeTeste
    {
        @Test
        void devolveTrueQuandoODiscordAceita()
        {
            assertThat( notifier().sendTest() ).isTrue();

            JsonNode embed = awaitEmbed();
            assertThat( embed.get( "title" ).asString() ).isEqualTo( "✅ Teste do Homelab Hub" );
            assertThat( embed.get( "color" ).asInt() ).isEqualTo( 0x2563EB );
        }

        @ParameterizedTest
        @ValueSource( ints = { 400, 401, 404, 429, 500 } )
        void devolveFalseQuandoODiscordRecusa( int code )
        {
            responseCode.set( code );

            assertThat( notifier().sendTest() ).isFalse();
        }

        @Test
        void devolveFalseQuandoODiscordEstaFora()
        {
            DiscordNotifier notifier = new DiscordNotifier(
                    "http://127.0.0.1:" + FakeHttpServer.closedPort() + "/hook", "x", JSON );

            assertThat( notifier.sendTest() ).isFalse();
        }

        @Test
        void falhaNoEnvioDeAvisoNaoLancaExcecao()
        {
            DiscordNotifier notifier = new DiscordNotifier(
                    "http://127.0.0.1:" + FakeHttpServer.closedPort() + "/hook", "x", JSON );

            assertThatNoException().isThrownBy( () -> notifier.notifyDown( "Jellyfin", SERVICE_URL, 502 ) );
        }
    }
}

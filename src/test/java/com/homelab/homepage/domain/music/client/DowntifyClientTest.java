package com.homelab.homepage.domain.music.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.homelab.homepage.support.FakeHttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DowntifyClientTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PLAYLIST = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";

    private FakeHttpServer downtify;
    /** Resposta das rotas do Downtify falso: codigo e corpo configuraveis por teste. */
    private final AtomicInteger code = new AtomicInteger( 200 );
    private final AtomicReference<String> body = new AtomicReference<>( "{}" );

    @BeforeEach
    void setUp()
    {
        downtify = new FakeHttpServer().handle( "/", exchange -> {
            String json = body.get();
            if ( json == null )
            {
                FakeHttpServer.reply( exchange, code.get() );
            }
            else
            {
                FakeHttpServer.replyJson( exchange, code.get(), json );
            }
        } );
    }

    @AfterEach
    void tearDown()
    {
        downtify.close();
    }

    private DowntifyClient client( String token )
    {
        return new DowntifyClient( downtify.url( "" ), token, Duration.ofSeconds( 2 ), JSON );
    }

    private DowntifyClient client()
    {
        return client( "dtfy_segredo" );
    }

    private FakeHttpServer.Received last()
    {
        return downtify.received().getLast();
    }

    private static JsonNode json( String raw )
    {
        return JSON.readTree( raw );
    }

    @Nested
    class Configuracao
    {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource( strings = { "  " } )
        void semUrlFicaDesativado( String url )
        {
            DowntifyClient client = new DowntifyClient( url, "x", Duration.ofSeconds( 1 ), JSON );

            assertThat( client.isConfigured() ).isFalse();
            assertThat( client.version() ).isEmpty();
            assertThatThrownBy( client::queue )
                    .isInstanceOf( DowntifyException.class )
                    .hasMessageContaining( "DOWNTIFY_URL" );
        }

        @Test
        void barraNoFinalDaUrlEIgnorada()
        {
            body.set( "[]" );
            new DowntifyClient( downtify.url( "/" ), "", Duration.ofSeconds( 2 ), JSON ).queue();

            assertThat( last().path() ).isEqualTo( "/api/queue" );
        }

        @Test
        void enviaOTokenComoBearer()
        {
            body.set( "[]" );
            client( " dtfy_segredo " ).queue();

            assertThat( last().authorization() ).isEqualTo( "Bearer dtfy_segredo" );
        }

        @Test
        void semTokenNaoEnviaAuthorization()
        {
            body.set( "[]" );
            client( "" ).queue();

            assertThat( last().authorization() ).isNull();
        }
    }

    @Nested
    class Versao
    {
        @Test
        void leAVersaoDoHealth()
        {
            body.set( "{\"status\":\"ok\",\"version\":\"3.12.0\"}" );

            assertThat( client().version() ).contains( "3.12.0" );
            assertThat( last().path() ).isEqualTo( "/api/health" );
        }

        @Test
        void healthSemVersao()
        {
            body.set( "{\"status\":\"ok\"}" );

            assertThat( client().version() ).contains( "desconhecida" );
        }

        @Test
        void foraDoArDevolveVazio()
        {
            DowntifyClient client = new DowntifyClient( "http://127.0.0.1:" + FakeHttpServer.closedPort(), "",
                    Duration.ofSeconds( 1 ), JSON );

            assertThat( client.version() ).isEmpty();
        }
    }

    @Nested
    class Resolver
    {
        @Test
        void playlistDevolveVariasFaixas()
        {
            body.set( "[{\"name\":\"A\"},{\"name\":\"B\"}]" );

            List<JsonNode> songs = client().resolve( PLAYLIST );

            assertThat( songs ).extracting( s -> s.get( "name" ).asString() ).containsExactly( "A", "B" );
            assertThat( last().method() ).isEqualTo( "GET" );
            assertThat( last().path() ).isEqualTo( "/api/song/url" );
            assertThat( last().query() ).isEqualTo( "url=" + PLAYLIST );
        }

        @Test
        void faixaDevolveUmObjeto()
        {
            body.set( "{\"name\":\"Unica\"}" );

            assertThat( client().resolve( PLAYLIST ) ).hasSize( 1 );
        }

        @Test
        void respostaVaziaNaoTemFaixas()
        {
            body.set( null );

            assertThat( client().resolve( PLAYLIST ) ).isEmpty();
        }
    }

    @Nested
    class Fila
    {
        @Test
        void downloadDePlaylistGeraM3u()
        {
            client().downloadBatch( List.of( json( "{\"name\":\"A\",\"song_id\":\"1\"}" ) ), PLAYLIST );

            JsonNode sent = json( last().body() );
            assertThat( last().method() ).isEqualTo( "POST" );
            assertThat( last().path() ).isEqualTo( "/api/download/batch" );
            assertThat( sent.get( "songs" ) ).hasSize( 1 );
            assertThat( sent.get( "songs" ).get( 0 ).get( "song_id" ).asString() ).isEqualTo( "1" );
            assertThat( sent.get( "playlist_url" ).asString() ).isEqualTo( PLAYLIST );
            assertThat( sent.get( "generate_m3u" ).asBoolean() ).isTrue();
        }

        @Test
        void downloadDeFaixaOuAlbumNaoGeraM3u()
        {
            client().downloadBatch( List.of( json( "{\"name\":\"A\"}" ) ), null );

            JsonNode sent = json( last().body() );
            assertThat( sent.has( "playlist_url" ) ).isFalse();
            assertThat( sent.has( "generate_m3u" ) ).isFalse();
        }

        @Test
        void listaAFila()
        {
            body.set( "[{\"status\":\"downloading\"},{\"status\":\"done\"}]" );

            assertThat( client().queue() ).hasSize( 2 );
        }

        @Test
        void filaComRespostaInesperadaFicaVazia()
        {
            body.set( "{\"erro\":true}" );

            assertThat( client().queue() ).isEmpty();
        }

        @Test
        void expoeOEnderecoSemBarraNoFinal()
        {
            assertThat( new DowntifyClient( "http://192.168.3.182:8582/", "", Duration.ofSeconds( 1 ), JSON ).baseUrl() )
                    .isEqualTo( "http://192.168.3.182:8582" );
        }
    }

    @Nested
    class Erros
    {
        @ParameterizedTest
        @CsvSource( {
                "401, confira o DOWNTIFY_TOKEN",
                "403, nao permite esta acao pelo dashboard",
                "404, nao encontrou nada para esse link",
                "500, respondeu HTTP 500",
                "429, respondeu HTTP 429" } )
        void codigosDeErroViramMensagens( int status, String message )
        {
            code.set( status );

            assertThatThrownBy( () -> client().resolve( PLAYLIST ) )
                    .isInstanceOf( DowntifyException.class )
                    .hasMessageContaining( message );
        }

        @Test
        void foraDoAr()
        {
            DowntifyClient client = new DowntifyClient( "http://127.0.0.1:" + FakeHttpServer.closedPort(), "",
                    Duration.ofSeconds( 1 ), JSON );

            assertThatThrownBy( () -> client.resolve( PLAYLIST ) )
                    .isInstanceOf( DowntifyException.class )
                    .hasMessageStartingWith( "Downtify indisponivel em http://127.0.0.1:" );
        }

        @Test
        void jsonInvalido()
        {
            body.set( "<html>login</html>" );

            assertThatThrownBy( () -> client().resolve( PLAYLIST ) )
                    .isInstanceOf( DowntifyException.class )
                    .hasMessage( "Resposta inesperada do Downtify" );
        }
    }

    @Nested
    class LeituraDeFaixas
    {
        @Test
        void textoDeCampo()
        {
            JsonNode song = json( "{\"name\":\"A\",\"vazio\":null}" );

            assertThat( DowntifyClient.text( song, "name" ) ).isEqualTo( "A" );
            assertThat( DowntifyClient.text( song, "vazio" ) ).isNull();
            assertThat( DowntifyClient.text( song, "ausente" ) ).isNull();
            assertThat( DowntifyClient.text( null, "name" ) ).isNull();
        }

        @Test
        void artistasEmLista()
        {
            assertThat( DowntifyClient.artists( json( "{\"artists\":[\"Daft Punk\",\"Pharrell\"]}" ) ) )
                    .isEqualTo( "Daft Punk, Pharrell" );
        }

        @Test
        void artistasComoObjetos()
        {
            assertThat( DowntifyClient.artists( json( "{\"artists\":[{\"name\":\"Daft Punk\"}]}" ) ) ).isEqualTo( "Daft Punk" );
        }

        @Test
        void artistaUnico()
        {
            assertThat( DowntifyClient.artists( json( "{\"artists\":[],\"artist\":\"Daft Punk\"}" ) ) ).isEqualTo( "Daft Punk" );
            assertThat( DowntifyClient.artists( json( "{}" ) ) ).isNull();
        }

        @Test
        void chaveDaFaixaPrefereSongId()
        {
            assertThat( DowntifyClient.songKey( json( "{\"song_id\":\"abc\",\"id\":\"x\",\"url\":\"u\"}" ) ) ).isEqualTo( "abc" );
            assertThat( DowntifyClient.songKey( json( "{\"id\":\"x\",\"url\":\"u\"}" ) ) ).isEqualTo( "x" );
            assertThat( DowntifyClient.songKey( json( "{\"song_id\":\"\",\"url\":\"u\"}" ) ) ).isEqualTo( "u" );
        }

        @Test
        void semIdentificadorUsaNomeEArtistas()
        {
            assertThat( DowntifyClient.songKey( json( "{\"name\":\"One More Time\",\"artists\":[\"Daft Punk\"]}" ) ) )
                    .isEqualTo( "one more time - daft punk" );
        }
    }
}

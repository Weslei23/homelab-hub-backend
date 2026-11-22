package com.homelab.homepage.domain.media.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
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

class ArrClientTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FakeHttpServer server;
    private final AtomicInteger code = new AtomicInteger( 200 );
    private final AtomicReference<String> body = new AtomicReference<>( "{}" );

    @BeforeEach
    void setUp()
    {
        server = new FakeHttpServer().handle( "/", exchange -> FakeHttpServer.replyJson( exchange, code.get(), body.get() ) );
    }

    @AfterEach
    void tearDown()
    {
        server.close();
    }

    private ArrClient sonarr()
    {
        return new ArrClient( ArrClient.Kind.SONARR, server.url( "/" ), " chave-sonarr ", Duration.ofSeconds( 2 ), JSON );
    }

    private ArrClient radarr()
    {
        return new ArrClient( ArrClient.Kind.RADARR, server.url( "" ), "chave-radarr", Duration.ofSeconds( 2 ), JSON );
    }

    private FakeHttpServer.Received last()
    {
        return server.received().getLast();
    }

    @Nested
    class Configuracao
    {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource( strings = { "  " } )
        void semUrlFicaDesativado( String url )
        {
            ArrClient client = new ArrClient( ArrClient.Kind.RADARR, url, "x", Duration.ofSeconds( 1 ), JSON );

            assertThat( client.isConfigured() ).isFalse();
            assertThatThrownBy( client::version ).isInstanceOf( MediaException.class ).hasMessage( "Radarr nao configurado no backend" );
        }

        @Test
        void enviaAChaveNoCabecalhoEIgnoraBarraFinal()
        {
            body.set( "{\"version\":\"4.0.20\"}" );

            assertThat( sonarr().version() ).isEqualTo( "4.0.20" );
            assertThat( last().path() ).isEqualTo( "/api/v3/system/status" );
            assertThat( sonarr().baseUrl() ).doesNotEndWith( "/" );
        }

        @Test
        void versaoAusente()
        {
            assertThat( radarr().version() ).isEqualTo( "desconhecida" );
        }
    }

    @Nested
    class Leitura
    {
        @Test
        void filaLeOsRegistrosDaPagina()
        {
            body.set( "{\"page\":1,\"records\":[{\"title\":\"a\"},{\"title\":\"b\"}]}" );

            assertThat( sonarr().queue() ).hasSize( 2 );
            assertThat( last().path() ).isEqualTo( "/api/v3/queue" );
            assertThat( last().query() ).contains( "includeSeries=true", "includeEpisode=true", "includeMovie=true" );
        }

        @Test
        void filaSemRegistros()
        {
            body.set( "{\"page\":1}" );

            assertThat( radarr().queue() ).isEmpty();
        }

        @Test
        void calendarioUsaAsDatas()
        {
            body.set( "[{\"title\":\"x\"}]" );

            assertThat( sonarr().calendar( LocalDate.of( 2026, 11, 22 ), LocalDate.of( 2026, 12, 6 ) ) ).hasSize( 1 );
            assertThat( last().query() ).contains( "start=2026-11-22", "end=2026-12-06" );
        }

        @Test
        void buscaPorNomeUsaORecursoCerto()
        {
            body.set( "[]" );

            sonarr().lookup( "The Office & Co" );
            assertThat( last().path() ).isEqualTo( "/api/v3/series/lookup" );
            assertThat( last().query() ).isEqualTo( "term=The Office & Co" );

            radarr().lookup( "Duna" );
            assertThat( last().path() ).isEqualTo( "/api/v3/movie/lookup" );
        }

        @Test
        void sonarrBuscaPorTvdb()
        {
            body.set( "[{\"title\":\"Reacher\",\"tvdbId\":366924}]" );

            assertThat( sonarr().lookupById( 366924 ) ).get().extracting( n -> n.get( "title" ).asString() ).isEqualTo( "Reacher" );
            assertThat( last().query() ).isEqualTo( "term=tvdb:366924" );
        }

        @Test
        void radarrBuscaPorTmdb()
        {
            body.set( "{\"title\":\"Duna\",\"tmdbId\":438631}" );

            assertThat( radarr().lookupById( 438631 ) ).isPresent();
            assertThat( last().path() ).isEqualTo( "/api/v3/movie/lookup/tmdb" );
            assertThat( last().query() ).isEqualTo( "tmdbId=438631" );
        }

        @Test
        void sonarrSemResultadoPorId()
        {
            body.set( "[]" );

            assertThat( sonarr().lookupById( 1 ) ).isEmpty();
        }

        @Test
        void perfisEPastas()
        {
            body.set( "[{\"id\":1}]" );

            assertThat( radarr().qualityProfiles() ).hasSize( 1 );
            assertThat( last().path() ).isEqualTo( "/api/v3/qualityprofile" );
            assertThat( radarr().rootFolders() ).hasSize( 1 );
            assertThat( last().path() ).isEqualTo( "/api/v3/rootfolder" );
        }
    }

    @Nested
    class Adicionar
    {
        @Test
        void postaNoRecursoDoTipo() throws Exception
        {
            body.set( "{\"id\":7}" );
            JsonNode movie = JSON.readTree( "{\"title\":\"Duna\",\"tmdbId\":438631}" );

            assertThat( radarr().add( movie ).get( "id" ).asInt() ).isEqualTo( 7 );
            assertThat( last().method() ).isEqualTo( "POST" );
            assertThat( last().path() ).isEqualTo( "/api/v3/movie" );
            assertThat( JSON.readTree( last().body() ).get( "tmdbId" ).asLong() ).isEqualTo( 438631 );
        }
    }

    @Nested
    class Erros
    {
        @ParameterizedTest
        @CsvSource( {
                "401, O Sonarr recusou o acesso: confira o SONARR_API_KEY",
                "403, O Sonarr recusou o acesso: confira o SONARR_API_KEY",
                "404, O Sonarr nao encontrou o que foi pedido",
                "500, O Sonarr respondeu HTTP 500" } )
        void codigosViramMensagens( int status, String message )
        {
            code.set( status );

            assertThatThrownBy( () -> sonarr().version() ).isInstanceOf( MediaException.class ).hasMessage( message );
        }

        @Test
        void validacaoMostraOMotivoDoArr()
        {
            code.set( 400 );
            body.set( "[{\"propertyName\":\"TmdbId\",\"errorMessage\":\"This movie has already been added\"}]" );

            assertThatThrownBy( () -> radarr().lookup( "x" ) ).hasMessage( "Radarr: This movie has already been added" );
        }

        @Test
        void validacaoComMensagemSimples()
        {
            code.set( 400 );
            body.set( "{\"message\":\"Invalid term\"}" );

            assertThatThrownBy( () -> radarr().lookup( "x" ) ).hasMessage( "Radarr: Invalid term" );
        }

        @Test
        void validacaoSemCorpoUtil()
        {
            code.set( 400 );
            body.set( "<html/>" );

            assertThatThrownBy( () -> radarr().lookup( "x" ) ).hasMessage( "Radarr: pedido recusado" );
        }

        @Test
        void foraDoAr()
        {
            ArrClient client = new ArrClient( ArrClient.Kind.RADARR, "http://127.0.0.1:" + FakeHttpServer.closedPort(), "",
                    Duration.ofSeconds( 1 ), JSON );

            assertThatThrownBy( client::version ).hasMessageStartingWith( "Radarr indisponivel em http://127.0.0.1:" );
        }

        @Test
        void respostaQueNaoEJson()
        {
            body.set( "<html>login</html>" );

            assertThatThrownBy( () -> sonarr().version() ).hasMessage( "Resposta inesperada do Sonarr" );
        }
    }
}

package com.homelab.homepage.domain.media.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.homelab.homepage.support.FakeHttpServer;

import tools.jackson.databind.json.JsonMapper;

class JellyfinClientTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FakeHttpServer server;
    private String users = "[{\"Id\":\"comum\",\"Policy\":{\"IsAdministrator\":false}},{\"Id\":\"admin\",\"Policy\":{\"IsAdministrator\":true}}]";

    @BeforeEach
    void setUp()
    {
        server = new FakeHttpServer()
                .handle( "/System/Info", e -> FakeHttpServer.replyJson( e, 200, "{\"Version\":\"10.11.10\"}" ) )
                .handle( "/Users", e -> FakeHttpServer.replyJson( e, 200, users ) )
                .handle( "/Items/Latest", e -> FakeHttpServer.replyJson( e, 200, "[{\"Id\":\"1\"},{\"Id\":\"2\"}]" ) )
                .handle( "/Sessions", e -> FakeHttpServer.replyJson( e, 200, "[{\"UserName\":\"ana\"}]" ) );
    }

    @AfterEach
    void tearDown()
    {
        server.close();
    }

    private JellyfinClient client( String key )
    {
        return new JellyfinClient( server.url( "/" ), key, Duration.ofSeconds( 2 ), JSON );
    }

    @Test
    void semUrlFicaDesativado()
    {
        assertThat( new JellyfinClient( "", "x", Duration.ofSeconds( 1 ), JSON ).isConfigured() ).isFalse();
    }

    @Test
    void enviaAChaveNoFormatoDoJellyfin()
    {
        assertThat( client( " abc " ).version() ).isEqualTo( "10.11.10" );
        assertThat( server.received().getLast().authorization() ).isEqualTo( "MediaBrowser Token=\"abc\"" );
    }

    @Test
    void semChaveNaoEnviaAuthorization()
    {
        client( "" ).version();

        assertThat( server.received().getLast().authorization() ).isNull();
    }

    @Test
    void recentesUsamOAdministradorEGuardamOId()
    {
        JellyfinClient client = client( "abc" );

        assertThat( client.latest( 16 ) ).hasSize( 2 );
        client.latest( 16 );

        assertThat( server.received( "/Users" ) ).hasSize( 1 );
        FakeHttpServer.Received latest = server.received( "/Items/Latest" ).getLast();
        assertThat( latest.query() ).contains( "userId=admin", "limit=16", "groupItems=true" );
    }

    @Test
    void semAdministradorUsaOPrimeiroUsuario()
    {
        users = "[{\"Id\":\"unico\"}]";

        client( "abc" ).latest( 5 );

        assertThat( server.received( "/Items/Latest" ).getLast().query() ).contains( "userId=unico" );
    }

    @Test
    void semUsuarios()
    {
        users = "[]";

        assertThatThrownBy( () -> client( "abc" ).latest( 5 ) ).hasMessage( "Nenhum usuario encontrado no Jellyfin" );
    }

    @Test
    void sessoes()
    {
        assertThat( client( "abc" ).sessions() ).hasSize( 1 );
        assertThat( server.received().getLast().query() ).isEqualTo( "activeWithinSeconds=960" );
    }

    @Test
    void enderecosDeImagemEPagina()
    {
        JellyfinClient client = client( "abc" );
        String base = server.url( "" );

        assertThat( client.imageUrl( "42" ) ).isEqualTo( base + "/Items/42/Images/Primary?fillHeight=300&quality=90" );
        assertThat( client.itemUrl( "42" ) ).isEqualTo( base + "/web/#/details?id=42" );
        assertThat( client.imageUrl( null ) ).isNull();
        assertThat( client.itemUrl( null ) ).isNull();
    }

    @Test
    void chaveRecusada()
    {
        FakeHttpServer recusa = new FakeHttpServer().respond( "/", 401 );
        try ( recusa )
        {
            JellyfinClient client = new JellyfinClient( recusa.url( "" ), "errada", Duration.ofSeconds( 1 ), JSON );

            assertThatThrownBy( client::version ).hasMessage( "O Jellyfin recusou o acesso: confira o JELLYFIN_API_KEY" );
        }
    }
}

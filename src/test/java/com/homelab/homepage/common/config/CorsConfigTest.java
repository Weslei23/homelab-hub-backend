package com.homelab.homepage.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.ServletRequestPathUtils;

class CorsConfigTest
{
    private static CorsConfiguration configFor( CorsConfigurationSource source, String path )
    {
        MockHttpServletRequest request = new MockHttpServletRequest( "GET", path );
        ServletRequestPathUtils.parseAndCache( request );
        return source.getCorsConfiguration( request );
    }

    private static CorsConfigurationSource source( String... origins )
    {
        return new CorsConfig( origins ).corsConfigurationSource();
    }

    @Test
    void limpaEspacosEIgnoraOrigensVazias()
    {
        CorsConfiguration api = configFor( source( " http://192.168.3.183:8082 ", "", "  ", "http://localhost:5173" ), "/api/catalog" );

        assertThat( api.getAllowedOrigins() ).containsExactly( "http://192.168.3.183:8082", "http://localhost:5173" );
    }

    @Test
    void apiAceitaOsMetodosDoCrud()
    {
        CorsConfiguration api = configFor( source( "http://localhost:5173" ), "/api/cards/1" );

        assertThat( api.getAllowedMethods() ).containsExactly( "GET", "POST", "PUT", "DELETE", "OPTIONS" );
        assertThat( api.getAllowedHeaders() ).containsExactly( "Authorization", "Content-Type", "Accept" );
        assertThat( api.getMaxAge() ).isEqualTo( 3600L );
    }

    @Test
    void documentacaoSoAceitaLeitura()
    {
        CorsConfiguration docs = configFor( source( "http://localhost:5173" ), "/v3/api-docs/swagger-config" );

        assertThat( docs.getAllowedMethods() ).containsExactly( "GET", "OPTIONS" );
        assertThat( docs.getAllowedOrigins() ).containsExactly( "http://localhost:5173" );
    }

    @Test
    void outrasRotasNaoTemCors()
    {
        assertThat( configFor( source( "http://localhost:5173" ), "/actuator/health" ) ).isNull();
    }

    @Test
    void origemPermitidaEOutraNao()
    {
        CorsConfiguration api = configFor( source( "http://localhost:5173" ), "/api/catalog" );

        assertThat( api.checkOrigin( "http://localhost:5173" ) ).isEqualTo( "http://localhost:5173" );
        assertThat( api.checkOrigin( "http://malicioso.com" ) ).isNull();
    }
}

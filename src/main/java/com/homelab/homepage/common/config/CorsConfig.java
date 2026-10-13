package com.homelab.homepage.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig
{

    private final List<String> allowedOrigins;

    public CorsConfig( @Value( "${app.cors.allowed-origins}" ) String[] allowedOrigins )
    {
        this.allowedOrigins = Arrays.stream( allowedOrigins )
                .map( String::trim )
                .filter( origin -> !origin.isEmpty() )
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource()
    {
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins( allowedOrigins );
        api.setAllowedMethods( List.of( "GET", "POST", "PUT", "DELETE", "OPTIONS" ) );
        api.setAllowedHeaders( List.of( "Authorization", "Content-Type", "Accept" ) );
        api.setMaxAge( 3600L );

        CorsConfiguration docs = new CorsConfiguration( api );
        docs.setAllowedMethods( List.of( "GET", "OPTIONS" ) );

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration( "/api/**", api );
        source.registerCorsConfiguration( "/v3/api-docs/**", docs );
        return source;
    }
}

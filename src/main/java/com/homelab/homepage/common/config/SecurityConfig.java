package com.homelab.homepage.common.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig
{
    @Autowired
    private SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain( HttpSecurity http ) throws Exception
    {

        return http
                .csrf( csrf -> csrf.disable() )
                .cors( cors -> cors.configure( http ) )
                .sessionManagement( session -> session.sessionCreationPolicy( SessionCreationPolicy.STATELESS ) )
                .authorizeHttpRequests( authorize -> authorize
                        .dispatcherTypeMatchers( DispatcherType.ERROR ).permitAll()
                        .requestMatchers( HttpMethod.POST, "/api/v1/auth/login" ).permitAll()
                        .requestMatchers( HttpMethod.POST, "/api/v1/auth/register" ).permitAll()
                        .requestMatchers( HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html" ).permitAll()

                        // USER e ADMIN podem visualizar o catalogo
                        .requestMatchers( HttpMethod.GET, "/api/catalog" ).hasAnyRole( "USER", "ADMIN" )

                        // Somente ADMIN cria, altera e remove categorias e cards
                        .requestMatchers( "/api/categories/**", "/api/cards/**" ).hasRole( "ADMIN" )

                        // Somente ADMIN gerencia usuarios e roles
                        .requestMatchers( "/api/v1/users/**" ).hasRole( "ADMIN" )

                        .anyRequest().authenticated() )
                .exceptionHandling( exceptions -> exceptions
                        // 401: sem login ou token invalido/expirado (o frontend volta para a tela de login)
                        .authenticationEntryPoint( ( request, response, exception ) -> writeJsonError( response, 401, "Autenticacao necessaria" ) )
                        // 403: logado, mas sem permissao (role insuficiente)
                        .accessDeniedHandler( ( request, response, exception ) -> writeJsonError( response, 403, "Voce nao tem permissao para esta acao" ) ) )
                .addFilterBefore( securityFilter, UsernamePasswordAuthenticationFilter.class )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager( AuthenticationConfiguration configuration ) throws Exception
    {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    private static void writeJsonError( HttpServletResponse response, int status, String message ) throws IOException
    {
        response.setStatus( status );
        response.setContentType( "application/json;charset=UTF-8" );
        response.getWriter().write( "{\"message\":\"" + message + "\"}" );
    }
}

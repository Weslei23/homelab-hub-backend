package com.homelab.homepage.common.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.homelab.homepage.domain.catalog.service.CatalogService;
import com.homelab.homepage.domain.music.service.MusicService;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;
import com.homelab.homepage.domain.status.service.StatusService;
import com.homelab.homepage.domain.user.model.JWTUserData;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.repository.UserRepository;
import com.homelab.homepage.domain.user.service.AuthService;
import com.homelab.homepage.domain.user.service.UserService;
import com.homelab.homepage.support.TestData;

/**
 * Regras de acesso de cada rota. Sobe so a camada web (sem banco): os services sao mocks,
 * entao aqui so importa se a requisicao passa ou e barrada pela seguranca.
 */
@WebMvcTest
@Import( { SecurityConfig.class, CorsConfig.class } )
class SecurityConfigTest
{
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private StatusService statusService;

    @MockitoBean
    private DiscordNotifier discordNotifier;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MusicService musicService;

    @MockitoBean
    private TokenConfig tokenConfig;

    @MockitoBean
    private UserRepository userRepository;

    private static final String CARD_JSON = "{\"name\":\"X\",\"url\":\"https://x\"}";

    private static MockHttpServletRequestBuilder json( MockHttpServletRequestBuilder builder, String body )
    {
        return builder.contentType( MediaType.APPLICATION_JSON ).content( body );
    }

    /** Rotas que qualquer usuario logado acessa. */
    static Stream<Arguments> leitura()
    {
        return Stream.of(
                Arguments.of( "GET /api/catalog", get( "/api/catalog" ), 200 ),
                Arguments.of( "GET /api/status", get( "/api/status" ), 200 ) );
    }

    /** Rotas exclusivas de ADMIN, com o status esperado quando o ADMIN chama. */
    static Stream<Arguments> somenteAdmin()
    {
        return Stream.of(
                Arguments.of( "POST /api/categories", json( post( "/api/categories" ), "{\"label\":\"X\"}" ), 201 ),
                Arguments.of( "PUT /api/categories/1", json( put( "/api/categories/1" ), "{\"label\":\"X\"}" ), 200 ),
                Arguments.of( "DELETE /api/categories/1", delete( "/api/categories/1" ), 204 ),
                Arguments.of( "POST /api/categories/1/cards", json( post( "/api/categories/1/cards" ), CARD_JSON ), 201 ),
                Arguments.of( "PUT /api/cards/1", json( put( "/api/cards/1" ), CARD_JSON ), 200 ),
                Arguments.of( "DELETE /api/cards/1", delete( "/api/cards/1" ), 204 ),
                Arguments.of( "GET /api/v1/users", get( "/api/v1/users" ), 200 ),
                Arguments.of( "PUT /api/v1/users/1/role", json( put( "/api/v1/users/1/role" ), "{\"role\":\"USER\"}" ), 200 ),
                // Sem webhook configurado o controller responde 400, mas passou pela seguranca
                Arguments.of( "POST /api/status/test-notification", post( "/api/status/test-notification" ), 400 ),
                Arguments.of( "GET /api/music/status", get( "/api/music/status" ), 200 ),
                Arguments.of( "POST /api/music/downloads", json( post( "/api/music/downloads" ),
                        "{\"url\":\"https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC\"}" ), 202 ),
                Arguments.of( "GET /api/music/queue", get( "/api/music/queue" ), 200 ),
                Arguments.of( "DELETE /api/music/queue/completed", delete( "/api/music/queue/completed" ), 204 ),
                Arguments.of( "GET /api/music/playlists", get( "/api/music/playlists" ), 200 ),
                Arguments.of( "POST /api/music/playlists", json( post( "/api/music/playlists" ),
                        "{\"url\":\"https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M\"}" ), 201 ),
                Arguments.of( "PUT /api/music/playlists/1", json( put( "/api/music/playlists/1" ), "{\"enabled\":false}" ), 200 ),
                Arguments.of( "DELETE /api/music/playlists/1", delete( "/api/music/playlists/1" ), 204 ),
                Arguments.of( "POST /api/music/playlists/1/sync", post( "/api/music/playlists/1/sync" ), 200 ) );
    }

    static Stream<Arguments> todasProtegidas()
    {
        return Stream.concat( leitura(), somenteAdmin() );
    }

    @Nested
    class Anonimo
    {
        @ParameterizedTest( name = "{0}" )
        @MethodSource( "com.homelab.homepage.common.config.SecurityConfigTest#todasProtegidas" )
        void precisaEstarLogado( String rota, MockHttpServletRequestBuilder request, int ignored ) throws Exception
        {
            mvc.perform( request )
                    .andExpect( status().isUnauthorized() )
                    .andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
                    .andExpect( jsonPath( "$.message" ).value( "Autenticacao necessaria" ) );
        }

        @Test
        void loginEPublico() throws Exception
        {
            mvc.perform( json( post( "/api/v1/auth/login" ), "{\"email\":\"a@b.c\",\"password\":\"x\"}" ) )
                    .andExpect( status().isOk() );
        }

        @Test
        void cadastroEPublico() throws Exception
        {
            mvc.perform( json( post( "/api/v1/auth/register" ), "{\"username\":\"a\",\"email\":\"a@b.c\",\"password\":\"x\"}" ) )
                    .andExpect( status().isCreated() );
        }

        @Test
        void tokenInvalidoContaComoAnonimo() throws Exception
        {
            when( tokenConfig.validateToken( "ruim" ) ).thenReturn( Optional.empty() );

            mvc.perform( get( "/api/catalog" ).header( "Authorization", "Bearer ruim" ) )
                    .andExpect( status().isUnauthorized() );
        }
    }

    @Nested
    class UsuarioComum
    {
        @ParameterizedTest( name = "{0}" )
        @MethodSource( "com.homelab.homepage.common.config.SecurityConfigTest#leitura" )
        void podeLer( String rota, MockHttpServletRequestBuilder request, int expected ) throws Exception
        {
            mvc.perform( request.with( user( "bia" ).roles( "USER" ) ) )
                    .andExpect( status().is( expected ) );
        }

        @ParameterizedTest( name = "{0}" )
        @MethodSource( "com.homelab.homepage.common.config.SecurityConfigTest#somenteAdmin" )
        void naoPodeAlterar( String rota, MockHttpServletRequestBuilder request, int ignored ) throws Exception
        {
            mvc.perform( request.with( user( "bia" ).roles( "USER" ) ) )
                    .andExpect( status().isForbidden() )
                    .andExpect( jsonPath( "$.message" ).value( "Voce nao tem permissao para esta acao" ) );
        }
    }

    @Nested
    class Administrador
    {
        @ParameterizedTest( name = "{0}" )
        @MethodSource( "com.homelab.homepage.common.config.SecurityConfigTest#todasProtegidas" )
        void podeTudo( String rota, MockHttpServletRequestBuilder request, int expected ) throws Exception
        {
            mvc.perform( request.with( user( "ana" ).roles( "ADMIN" ) ) )
                    .andExpect( status().is( expected ) );
        }
    }

    @Nested
    class TokenJwt
    {
        private void tokenFor( String email, Role roleNoBanco )
        {
            when( tokenConfig.validateToken( "jwt" ) )
                    .thenReturn( Optional.of( JWTUserData.builder().userId( 1L ).email( email ).build() ) );
            when( userRepository.findUserByEmail( email ) )
                    .thenReturn( Optional.of( TestData.user( 1L, email, roleNoBanco ) ) );
        }

        @Test
        void adminPeloTokenPodeExcluir() throws Exception
        {
            tokenFor( "ana@lab.local", Role.ADMIN );

            mvc.perform( delete( "/api/cards/1" ).header( "Authorization", "Bearer jwt" ) )
                    .andExpect( status().isNoContent() );
        }

        @Test
        void roleVemDoBancoENaoDoToken() throws Exception
        {
            // Usuario rebaixado no banco: o mesmo token deixa de dar acesso de admin
            tokenFor( "ana@lab.local", Role.USER );

            mvc.perform( delete( "/api/cards/1" ).header( "Authorization", "Bearer jwt" ) )
                    .andExpect( status().isForbidden() );
            mvc.perform( get( "/api/catalog" ).header( "Authorization", "Bearer jwt" ) )
                    .andExpect( status().isOk() );
        }
    }
}

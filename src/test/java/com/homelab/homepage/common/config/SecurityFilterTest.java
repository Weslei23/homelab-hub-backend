package com.homelab.homepage.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.domain.user.model.JWTUserData;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.repository.UserRepository;
import com.homelab.homepage.support.TestData;

class SecurityFilterTest
{
    private TokenConfig tokenConfig;
    private UserRepository userRepository;
    private SecurityFilter filter;
    private MockFilterChain chain;

    @BeforeEach
    void setUp()
    {
        tokenConfig = mock( TokenConfig.class );
        userRepository = mock( UserRepository.class );
        filter = new SecurityFilter();
        ReflectionTestUtils.setField( filter, "tokenConfig", tokenConfig );
        ReflectionTestUtils.setField( filter, "userRepository", userRepository );
        chain = new MockFilterChain();
    }

    @AfterEach
    void clearContext()
    {
        SecurityContextHolder.clearContext();
    }

    private void run( String authorization ) throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest( "GET", "/api/catalog" );
        if ( authorization != null )
        {
            request.addHeader( "Authorization", authorization );
        }
        filter.doFilter( request, new MockHttpServletResponse(), chain );
    }

    private static Authentication authentication()
    {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void tokenIsValidFor( String token, String email )
    {
        when( tokenConfig.validateToken( token ) )
                .thenReturn( Optional.of( JWTUserData.builder().userId( 1L ).email( email ).build() ) );
    }

    @Test
    void semCabecalhoSegueSemAutenticar() throws Exception
    {
        run( null );

        assertThat( authentication() ).isNull();
        assertThat( chain.getRequest() ).isNotNull();
        verify( tokenConfig, never() ).validateToken( any() );
    }

    @Test
    void cabecalhoQueNaoEBearerEIgnorado() throws Exception
    {
        run( "Basic dXNlcjpwYXNz" );

        assertThat( authentication() ).isNull();
        verify( tokenConfig, never() ).validateToken( any() );
    }

    @Test
    void tokenInvalidoSegueSemAutenticar() throws Exception
    {
        when( tokenConfig.validateToken( "ruim" ) ).thenReturn( Optional.empty() );

        run( "Bearer ruim" );

        assertThat( authentication() ).isNull();
        assertThat( chain.getRequest() ).isNotNull();
    }

    @Test
    void tokenDeUsuarioExcluidoSegueSemAutenticar() throws Exception
    {
        tokenIsValidFor( "ok", "sumiu@lab.local" );
        when( userRepository.findUserByEmail( "sumiu@lab.local" ) ).thenReturn( Optional.empty() );

        run( "Bearer ok" );

        assertThat( authentication() ).isNull();
    }

    @Test
    void tokenValidoAutenticaComARoleDoBanco() throws Exception
    {
        tokenIsValidFor( "ok", "ana@lab.local" );
        when( userRepository.findUserByEmail( "ana@lab.local" ) )
                .thenReturn( Optional.of( TestData.user( 5L, "ana@lab.local", Role.ADMIN ) ) );

        run( "Bearer ok" );

        Authentication auth = authentication();
        assertThat( auth ).isNotNull();
        assertThat( auth.isAuthenticated() ).isTrue();
        assertThat( auth.getAuthorities() ).extracting( GrantedAuthority::getAuthority ).containsExactly( "ROLE_ADMIN" );
        JWTUserData principal = (JWTUserData) auth.getPrincipal();
        assertThat( principal.getUserId() ).isEqualTo( 5L );
        assertThat( principal.getEmail() ).isEqualTo( "ana@lab.local" );
        assertThat( principal.getRole() ).isEqualTo( Role.ADMIN );
        assertThat( auth.getCredentials() ).isNull();
        assertThat( chain.getRequest() ).isNotNull();
    }

    @Test
    void usuarioRebaixadoPerdeOAcessoNaHora() throws Exception
    {
        tokenIsValidFor( "ok", "ana@lab.local" );
        when( userRepository.findUserByEmail( "ana@lab.local" ) )
                .thenReturn( Optional.of( TestData.user( 5L, "ana@lab.local", Role.USER ) ) );

        run( "Bearer ok" );

        assertThat( authentication().getAuthorities() ).extracting( GrantedAuthority::getAuthority )
                .containsExactly( "ROLE_USER" );
    }
}

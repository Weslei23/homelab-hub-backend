package com.homelab.homepage.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.common.config.TokenConfig;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.ForbiddenException;
import com.homelab.homepage.domain.user.dto.request.LoginRequestDTO;
import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.LoginResponseDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import com.homelab.homepage.support.TestData;

@ExtendWith( MockitoExtension.class )
class AuthServiceTest
{
    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenConfig tokenConfig;

    @InjectMocks
    private AuthService authService;

    @Nested
    class Login
    {
        private LoginRequestDTO request( String email, String password )
        {
            LoginRequestDTO dto = new LoginRequestDTO();
            dto.setEmail( email );
            dto.setPassword( password );
            return dto;
        }

        @Test
        void autenticaEDevolveTokenEDadosDoUsuario()
        {
            User user = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
            when( authenticationManager.authenticate( any() ) )
                    .thenReturn( new UsernamePasswordAuthenticationToken( user, null, user.getAuthorities() ) );
            when( tokenConfig.getSecretKey( user ) ).thenReturn( "jwt-token" );

            LoginResponseDTO response = authService.login( request( "ana@lab.local", "segredo" ) );

            assertThat( response.getToken() ).isEqualTo( "jwt-token" );
            assertThat( response.getUsername() ).isEqualTo( "ana" );
            assertThat( response.getEmail() ).isEqualTo( "ana@lab.local" );
            assertThat( response.getRole() ).isEqualTo( Role.ADMIN );
        }

        @Test
        void enviaEmailESenhaParaOAuthenticationManager()
        {
            User user = TestData.user( 1L, "ana@lab.local", Role.USER );
            when( authenticationManager.authenticate( any() ) )
                    .thenReturn( new UsernamePasswordAuthenticationToken( user, null ) );

            authService.login( request( "ana@lab.local", "segredo" ) );

            ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass( Authentication.class );
            verify( authenticationManager ).authenticate( captor.capture() );
            assertThat( captor.getValue().getPrincipal() ).isEqualTo( "ana@lab.local" );
            assertThat( captor.getValue().getCredentials() ).isEqualTo( "segredo" );
        }

        @Test
        void credenciaisInvalidasPropagamAExcecao()
        {
            when( authenticationManager.authenticate( any() ) ).thenThrow( new BadCredentialsException( "x" ) );

            assertThatThrownBy( () -> authService.login( request( "ana@lab.local", "errada" ) ) )
                    .isInstanceOf( BadCredentialsException.class );
            verify( tokenConfig, never() ).getSecretKey( any() );
        }
    }

    @Nested
    class Cadastro
    {
        @BeforeEach
        void cadastroLigado()
        {
            ReflectionTestUtils.setField( authService, "registrationEnabled", true );
        }

        private RegisterUserRequestDTO request()
        {
            RegisterUserRequestDTO dto = new RegisterUserRequestDTO();
            dto.setUsername( "bia" );
            dto.setEmail( "bia@lab.local" );
            dto.setPassword( "senha-forte" );
            return dto;
        }

        @Test
        void criaUsuarioComumComSenhaCriptografada()
        {
            when( userRepository.existsByEmail( "bia@lab.local" ) ).thenReturn( false );
            when( passwordEncoder.encode( "senha-forte" ) ).thenReturn( "$2a$hash" );

            RegisterUserResponseDTO response = authService.register( request() );

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass( User.class );
            verify( userRepository ).save( saved.capture() );
            assertThat( saved.getValue().getUsername() ).isEqualTo( "bia" );
            assertThat( saved.getValue().getEmail() ).isEqualTo( "bia@lab.local" );
            assertThat( saved.getValue().getPassword() ).isEqualTo( "$2a$hash" );
            assertThat( saved.getValue().getRole() ).isEqualTo( Role.USER );
            assertThat( response.getUsername() ).isEqualTo( "bia" );
            assertThat( response.getEmail() ).isEqualTo( "bia@lab.local" );
        }

        @Test
        void emailJaCadastradoFalha()
        {
            when( userRepository.existsByEmail( "bia@lab.local" ) ).thenReturn( true );

            assertThatThrownBy( () -> authService.register( request() ) )
                    .isInstanceOf( ConflictException.class )
                    .hasMessage( "E-mail ja cadastrado" );
            verify( userRepository, never() ).save( any() );
            verify( passwordEncoder, never() ).encode( any() );
        }

        @Test
        void cadastroDesligadoRecusaAntesDeTudo()
        {
            ReflectionTestUtils.setField( authService, "registrationEnabled", false );

            assertThat( authService.isRegistrationEnabled() ).isFalse();
            assertThatThrownBy( () -> authService.register( request() ) )
                    .isInstanceOf( ForbiddenException.class )
                    .hasMessage( "Cadastro desativado. Peca uma conta ao administrador" );
            verify( userRepository, never() ).existsByEmail( any() );
            verify( userRepository, never() ).save( any() );
        }
    }

    @Test
    void cadastroVemDesligadoPorPadrao()
    {
        assertThat( authService.isRegistrationEnabled() ).isFalse();
    }
}

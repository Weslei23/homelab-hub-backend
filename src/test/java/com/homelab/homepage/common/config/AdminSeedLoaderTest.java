package com.homelab.homepage.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import com.homelab.homepage.support.TestData;

class AdminSeedLoaderTest
{
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp()
    {
        userRepository = mock( UserRepository.class );
        passwordEncoder = mock( PasswordEncoder.class );
    }

    private void run( String email, String password )
    {
        new AdminSeedLoader( userRepository, passwordEncoder, email, "admin", password )
                .run( new DefaultApplicationArguments() );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource( strings = { "   " } )
    void semAdminEmailNaoAlteraNada( String email )
    {
        when( userRepository.countByRole( Role.ADMIN ) ).thenReturn( 0L );

        run( email, "senha" );

        verify( userRepository ).countByRole( Role.ADMIN );
        verify( userRepository, never() ).findByEmail( any() );
        verify( userRepository, never() ).save( any() );
    }

    @Test
    void promoveUsuarioExistente()
    {
        User bia = TestData.user( 2L, "bia@lab.local", Role.USER );
        when( userRepository.findByEmail( "bia@lab.local" ) ).thenReturn( Optional.of( bia ) );

        run( "bia@lab.local", "" );

        assertThat( bia.getRole() ).isEqualTo( Role.ADMIN );
        assertThat( bia.getPassword() ).isEqualTo( "hash" );
        verify( userRepository ).save( bia );
        verify( passwordEncoder, never() ).encode( any() );
    }

    @Test
    void ignoraEspacosNoEmail()
    {
        when( userRepository.findByEmail( "bia@lab.local" ) )
                .thenReturn( Optional.of( TestData.user( 2L, "bia@lab.local", Role.USER ) ) );

        run( "  bia@lab.local  ", "" );

        verify( userRepository ).findByEmail( "bia@lab.local" );
    }

    @Test
    void adminComAMesmaSenhaNaoEAlterado()
    {
        User ana = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
        when( userRepository.findByEmail( "ana@lab.local" ) ).thenReturn( Optional.of( ana ) );
        when( passwordEncoder.matches( "senha-atual", "hash" ) ).thenReturn( true );

        run( "ana@lab.local", "senha-atual" );

        verify( userRepository, never() ).save( any() );
        verify( passwordEncoder, never() ).encode( any() );
    }

    @Test
    void senhaNovaNoAdminPasswordTrocaASenhaDoAdmin()
    {
        // Cenario real: a credencial homepage-admin-password foi alterada no Jenkins
        User ana = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
        when( userRepository.findByEmail( "ana@lab.local" ) ).thenReturn( Optional.of( ana ) );
        when( passwordEncoder.matches( "senha-nova", "hash" ) ).thenReturn( false );
        when( passwordEncoder.encode( "senha-nova" ) ).thenReturn( "$2a$novo-hash" );

        run( "ana@lab.local", "senha-nova" );

        assertThat( ana.getPassword() ).isEqualTo( "$2a$novo-hash" );
        assertThat( ana.getRole() ).isEqualTo( Role.ADMIN );
        verify( userRepository ).save( ana );
    }

    @Test
    void usuarioComumEPromovidoEGanhaASenhaNumaUnicaGravacao()
    {
        User bia = TestData.user( 2L, "bia@lab.local", Role.USER );
        when( userRepository.findByEmail( "bia@lab.local" ) ).thenReturn( Optional.of( bia ) );
        when( passwordEncoder.matches( "senha-nova", "hash" ) ).thenReturn( false );
        when( passwordEncoder.encode( "senha-nova" ) ).thenReturn( "$2a$novo-hash" );

        run( "bia@lab.local", "senha-nova" );

        assertThat( bia.getRole() ).isEqualTo( Role.ADMIN );
        assertThat( bia.getPassword() ).isEqualTo( "$2a$novo-hash" );
        verify( userRepository, org.mockito.Mockito.times( 1 ) ).save( bia );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource( strings = { "   " } )
    void semAdminPasswordASenhaExistenteEMantida( String password )
    {
        User ana = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
        when( userRepository.findByEmail( "ana@lab.local" ) ).thenReturn( Optional.of( ana ) );

        run( "ana@lab.local", password );

        assertThat( ana.getPassword() ).isEqualTo( "hash" );
        verify( passwordEncoder, never() ).matches( any(), any() );
        verify( userRepository, never() ).save( any() );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource( strings = { "  " } )
    void naoCriaAdminSemSenha( String password )
    {
        when( userRepository.findByEmail( "novo@lab.local" ) ).thenReturn( Optional.empty() );

        run( "novo@lab.local", password );

        verify( userRepository, never() ).save( any() );
    }

    @Test
    void criaAdminComSenhaCriptografada()
    {
        when( userRepository.findByEmail( "novo@lab.local" ) ).thenReturn( Optional.empty() );
        when( passwordEncoder.encode( "senha-forte" ) ).thenReturn( "$2a$hash" );

        run( "novo@lab.local", "senha-forte" );

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass( User.class );
        verify( userRepository ).save( saved.capture() );
        assertThat( saved.getValue().getEmail() ).isEqualTo( "novo@lab.local" );
        assertThat( saved.getValue().getUsername() ).isEqualTo( "admin" );
        assertThat( saved.getValue().getPassword() ).isEqualTo( "$2a$hash" );
        assertThat( saved.getValue().getRole() ).isEqualTo( Role.ADMIN );
    }
}

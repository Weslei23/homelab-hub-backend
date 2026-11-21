package com.homelab.homepage.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import com.homelab.homepage.support.TestData;

class AuthConfigTest
{
    private UserRepository userRepository;
    private AuthConfig authConfig;

    @BeforeEach
    void setUp()
    {
        userRepository = mock( UserRepository.class );
        authConfig = new AuthConfig();
        ReflectionTestUtils.setField( authConfig, "userRepository", userRepository );
    }

    @Test
    void buscaOUsuarioPeloEmail()
    {
        User ana = TestData.user( 1L, "ana@lab.local", Role.USER );
        when( userRepository.findUserByEmail( "ana@lab.local" ) ).thenReturn( Optional.of( ana ) );

        assertThat( authConfig.loadUserByUsername( "ana@lab.local" ) ).isSameAs( ana );
    }

    @Test
    void emailDesconhecidoLancaUsernameNotFound()
    {
        when( userRepository.findUserByEmail( "x@lab.local" ) ).thenReturn( Optional.empty() );

        assertThatThrownBy( () -> authConfig.loadUserByUsername( "x@lab.local" ) )
                .isInstanceOf( UsernameNotFoundException.class )
                .hasMessage( "x@lab.local" );
    }
}

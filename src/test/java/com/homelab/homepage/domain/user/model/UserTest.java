package com.homelab.homepage.domain.user.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class UserTest
{
    @Test
    void novoUsuarioEUser()
    {
        assertThat( new User().getRole() ).isEqualTo( Role.USER );
    }

    @Test
    void roleNulaDoBancoLegadoViraUser()
    {
        User user = new User();
        user.setRole( null );

        assertThat( user.getRole() ).isEqualTo( Role.USER );
        assertThat( user.getAuthorities() ).extracting( GrantedAuthority::getAuthority ).containsExactly( "ROLE_USER" );
    }

    @Test
    void authorityDoAdmin()
    {
        User user = new User();
        user.setRole( Role.ADMIN );

        assertThat( user.getAuthorities() ).extracting( GrantedAuthority::getAuthority ).containsExactly( "ROLE_ADMIN" );
    }

    @Test
    void contaSempreAtiva()
    {
        User user = new User();

        assertThat( user.isAccountNonExpired() ).isTrue();
        assertThat( user.isAccountNonLocked() ).isTrue();
        assertThat( user.isCredentialsNonExpired() ).isTrue();
        assertThat( user.isEnabled() ).isTrue();
    }

    @Test
    void usernameDoSpringSecurityEOCampoUsername()
    {
        User user = new User();
        user.setUsername( "ana" );
        user.setEmail( "ana@lab.local" );

        // Atencao: o login usa o e-mail (AuthConfig), mas getUsername() devolve o nome de exibicao
        assertThat( user.getUsername() ).isEqualTo( "ana" );
    }
}

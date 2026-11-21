package com.homelab.homepage.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.user.dto.response.UserResponseDTO;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import com.homelab.homepage.support.TestData;

@ExtendWith( MockitoExtension.class )
class UserServiceTest
{
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void listaUsuariosSemExporASenha()
    {
        when( userRepository.findAll() ).thenReturn( List.of(
                TestData.user( 1L, "ana@lab.local", Role.ADMIN ),
                TestData.user( 2L, "bia@lab.local", Role.USER ) ) );

        assertThat( userService.listUsers() ).containsExactly(
                new UserResponseDTO( 1L, "ana", "ana@lab.local", Role.ADMIN ),
                new UserResponseDTO( 2L, "bia", "bia@lab.local", Role.USER ) );
    }

    @Test
    void promoveUsuarioAAdmin()
    {
        User bia = TestData.user( 2L, "bia@lab.local", Role.USER );
        when( userRepository.findById( 2L ) ).thenReturn( Optional.of( bia ) );

        UserResponseDTO response = userService.updateRole( 2L, Role.ADMIN );

        assertThat( bia.getRole() ).isEqualTo( Role.ADMIN );
        assertThat( response.role() ).isEqualTo( Role.ADMIN );
        verify( userRepository, never() ).countByRole( any() );
    }

    @Test
    void rebaixaAdminQuandoHaOutros()
    {
        User ana = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
        when( userRepository.findById( 1L ) ).thenReturn( Optional.of( ana ) );
        when( userRepository.countByRole( Role.ADMIN ) ).thenReturn( 2L );

        assertThat( userService.updateRole( 1L, Role.USER ).role() ).isEqualTo( Role.USER );
        assertThat( ana.getRole() ).isEqualTo( Role.USER );
    }

    @Test
    void naoRebaixaOUltimoAdmin()
    {
        User ana = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
        when( userRepository.findById( 1L ) ).thenReturn( Optional.of( ana ) );
        when( userRepository.countByRole( Role.ADMIN ) ).thenReturn( 1L );

        assertThatThrownBy( () -> userService.updateRole( 1L, Role.USER ) )
                .isInstanceOf( ConflictException.class )
                .hasMessage( "Deve existir ao menos um administrador" );
        assertThat( ana.getRole() ).isEqualTo( Role.ADMIN );
    }

    @Test
    void manterOUltimoAdminComoAdminEPermitido()
    {
        User ana = TestData.user( 1L, "ana@lab.local", Role.ADMIN );
        when( userRepository.findById( 1L ) ).thenReturn( Optional.of( ana ) );

        assertThat( userService.updateRole( 1L, Role.ADMIN ).role() ).isEqualTo( Role.ADMIN );
        verify( userRepository, never() ).countByRole( any() );
    }

    @Test
    void usuarioInexistenteFalha()
    {
        when( userRepository.findById( 99L ) ).thenReturn( Optional.empty() );

        assertThatThrownBy( () -> userService.updateRole( 99L, Role.ADMIN ) )
                .isInstanceOf( ResourceNotFoundException.class )
                .hasMessage( "Usuario nao encontrado" );
    }
}

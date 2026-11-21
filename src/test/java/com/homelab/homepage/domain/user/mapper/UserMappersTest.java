package com.homelab.homepage.domain.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.dto.response.UserResponseDTO;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.support.TestData;

class UserMappersTest
{
    @Test
    void userMapperNaoExpoeASenha()
    {
        User user = TestData.user( 1L, "ana@lab.local", Role.ADMIN );

        assertThat( UserMapper.toResponse( user ) ).isEqualTo( new UserResponseDTO( 1L, "ana", "ana@lab.local", Role.ADMIN ) );
    }

    @Test
    void registerMapperDtoParaEntidade()
    {
        RegisterUserRequestDTO dto = new RegisterUserRequestDTO();
        dto.setUsername( "bia" );
        dto.setEmail( "bia@lab.local" );
        dto.setPassword( "senha" );

        User user = RegisterMapper.dtoToEntity( dto );

        assertThat( user.getUsername() ).isEqualTo( "bia" );
        assertThat( user.getEmail() ).isEqualTo( "bia@lab.local" );
        assertThat( user.getPassword() ).isEqualTo( "senha" );
        assertThat( user.getId() ).isNull();
        assertThat( user.getRole() ).isEqualTo( Role.USER );
    }

    @Test
    void registerMapperEntidadeParaDto()
    {
        User user = TestData.user( 1L, "bia@lab.local", Role.USER );

        RegisterUserResponseDTO dto = RegisterMapper.entityToDto( user );

        assertThat( dto.getUsername() ).isEqualTo( "bia" );
        assertThat( dto.getEmail() ).isEqualTo( "bia@lab.local" );
    }
}

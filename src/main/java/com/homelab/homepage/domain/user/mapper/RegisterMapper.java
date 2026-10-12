package com.homelab.homepage.domain.user.mapper;

import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.model.User;

public class RegisterMapper
{
    /**
     * Entity to dto
     *
     * @param user
     * @return
     */
    public static RegisterUserResponseDTO entityToDto( User user )
    {
        RegisterUserResponseDTO responseDTO = new RegisterUserResponseDTO( user.getUsername(), user.getEmail() );

        return responseDTO;
    }

    /**
     * Dto to entity
     *
     * @param requestDTO
     * @return
     */
    public static User dtoToEntity( RegisterUserRequestDTO requestDTO )
    {
        User user = new User();

        user.setUsername( requestDTO.getUsername() );
        user.setEmail( requestDTO.getEmail() );
        user.setPassword( requestDTO.getPassword() );

        return user;
    }
}
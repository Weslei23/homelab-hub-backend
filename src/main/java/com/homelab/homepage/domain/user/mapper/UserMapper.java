package com.homelab.homepage.domain.user.mapper;

import com.homelab.homepage.domain.user.dto.response.UserResponseDTO;
import com.homelab.homepage.domain.user.model.User;

public final class UserMapper
{

    private UserMapper()
    {
    }

    public static UserResponseDTO toResponse( User user )
    {
        return new UserResponseDTO( user.getId(), user.getUsername(), user.getEmail(), user.getRole() );
    }
}

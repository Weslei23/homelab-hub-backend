package com.homelab.homepage.domain.user.dto.response;

import com.homelab.homepage.domain.user.model.Role;

public record UserResponseDTO( Long id, String username, String email, Role role )
{
}

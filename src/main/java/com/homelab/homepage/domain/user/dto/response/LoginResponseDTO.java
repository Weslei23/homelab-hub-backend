package com.homelab.homepage.domain.user.dto.response;

import com.homelab.homepage.domain.user.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDTO
{
    private String token;
    private String username;
    private String email;
    private Role role;
}

package com.homelab.homepage.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterUserResponseDTO
{
    private String username;
    private String email;
}
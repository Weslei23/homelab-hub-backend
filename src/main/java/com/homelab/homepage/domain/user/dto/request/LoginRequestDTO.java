package com.homelab.homepage.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO
{
    @NotBlank( message = "argument 'email' must not be null or empty." )
    private String email;

    @NotBlank( message = "argument 'password' must not be null or empty." )
    private String password;
}
package com.homelab.homepage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank( message = "O nome da categoria e obrigatorio" )
        @Size( max = 100, message = "O nome da categoria pode ter no maximo 100 caracteres" )
        String label )
{
}

package com.homelab.homepage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CardRequest(
        @NotBlank( message = "O nome e obrigatorio" )
        @Size( max = 100, message = "O nome pode ter no maximo 100 caracteres" )
        String name,

        @NotBlank( message = "A URL e obrigatoria" )
        @Size( max = 2048, message = "A URL pode ter no maximo 2048 caracteres" )
        // Apenas http/https: evita esquemas perigosos como javascript:
        @Pattern( regexp = "(?i)^https?://\\S+$", message = "A URL deve comecar com http:// ou https://" )
        String url,

        @Size( max = 255, message = "A descricao pode ter no maximo 255 caracteres" )
        String description,

        @Size( max = 2048, message = "Icone invalido" )
        @Pattern( regexp = "(?i)^([a-z0-9-]{1,60}|https?://\\S+)$", message = "Icone invalido" )
        String icon )
{
}

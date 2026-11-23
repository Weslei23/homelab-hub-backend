package com.homelab.homepage.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequestDTO
{
    @NotBlank( message = "argument 'username' must not be null or empty." )
    @Size( min = 3, max = 50, message = "O nome de usuario deve ter de 3 a 50 caracteres" )
    private String username;

    @NotBlank( message = "argument 'email' must not be null or empty." )
    @Email( message = "Informe um e-mail valido" )
    @Size( max = 254, message = "E-mail muito longo" )
    private String email;

    /** O BCrypt so considera os primeiros 72 bytes da senha. */
    @NotBlank( message = "argument 'password' must not be null or empty." )
    @Size( min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres" )
    private String password;
}

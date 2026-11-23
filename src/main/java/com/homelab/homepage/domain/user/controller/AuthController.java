package com.homelab.homepage.domain.user.controller;

import java.util.Map;

import com.homelab.homepage.domain.user.dto.request.LoginRequestDTO;
import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.LoginResponseDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.service.AuthService;
import com.homelab.homepage.domain.user.service.LoginAttemptService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping( "/api/v1/auth" )
public class AuthController
{
    @Autowired
    private AuthService authService;

    @Autowired
    private LoginAttemptService loginAttempts;

    /** Configuracao publica da tela de login (ex.: se o botao "Criar conta" aparece). */
    @GetMapping( "/config" )
    public Map<String, Boolean> config()
    {
        return Map.of( "registrationEnabled", authService.isRegistrationEnabled() );
    }

    @PostMapping( "/login" )
    public ResponseEntity<LoginResponseDTO> login( @Valid @RequestBody LoginRequestDTO loginRequestDTO, HttpServletRequest request )
    {
        String ip = clientIp( request );
        String email = loginRequestDTO.getEmail();
        loginAttempts.checkAllowed( ip, email );
        try
        {
            LoginResponseDTO response = authService.login( loginRequestDTO );
            loginAttempts.recordSuccess( ip, email );
            return ResponseEntity.ok( response );
        }
        catch ( AuthenticationException e )
        {
            loginAttempts.recordFailure( ip, email );
            throw e;
        }
    }

    @PostMapping( "/register" )
    public ResponseEntity<RegisterUserResponseDTO> register( @Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO )
    {
        return ResponseEntity.status( HttpStatus.CREATED ).body( authService.register( registerUserRequestDTO ) );
    }

    /**
     * IP de quem fez o login. Em producao o backend so e alcancado pelo nginx do frontend
     * (porta publicada apenas em 127.0.0.1), que preenche X-Real-IP com o IP real do navegador.
     */
    static String clientIp( HttpServletRequest request )
    {
        String realIp = request.getHeader( "X-Real-IP" );
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }
}

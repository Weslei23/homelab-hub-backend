package com.homelab.homepage.domain.user.controller;

import com.homelab.homepage.domain.user.dto.request.LoginRequestDTO;
import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.LoginResponseDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping( "/api/v1/auth" )
public class AuthController
{
    @Autowired
    private AuthService authService;

    @PostMapping( "/login" )
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO )
    {
        return ResponseEntity.ok( authService.login( loginRequestDTO) );
    }

    @PostMapping( "/register" )
    public ResponseEntity<RegisterUserResponseDTO> register(@Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO )
    {
        return ResponseEntity.status( HttpStatus.CREATED ).body( authService.register( registerUserRequestDTO) );
    }
}

package com.homelab.homepage.domain.user.service;

import com.homelab.homepage.common.config.TokenConfig;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.ForbiddenException;
import com.homelab.homepage.domain.user.dto.request.LoginRequestDTO;
import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.LoginResponseDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.mapper.RegisterMapper;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService
{
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenConfig tokenConfig;

    /** Cadastro publico ligado? Desligado por padrao: contas novas so com REGISTRATION_ENABLED=true. */
    @Value( "${app.auth.registration-enabled:false}" )
    private boolean registrationEnabled;

    public boolean isRegistrationEnabled()
    {
        return registrationEnabled;
    }

    /**
     * Login
     * @param loginRequestDTO
     * @return
     */
    public LoginResponseDTO login( LoginRequestDTO loginRequestDTO )
    {
        UsernamePasswordAuthenticationToken userAndPass = new UsernamePasswordAuthenticationToken( loginRequestDTO.getEmail(), loginRequestDTO.getPassword() );
        Authentication authentication = authenticationManager.authenticate( userAndPass );

        User user = (User) authentication.getPrincipal();

        String token = tokenConfig.getSecretKey( user );

        return new LoginResponseDTO( token, user.getUsername(), user.getEmail(), user.getRole() );
    }

    /**
     * Register
     * @param registerUserRequestDTO
     * @return
     */
    public RegisterUserResponseDTO register( RegisterUserRequestDTO registerUserRequestDTO )
    {
        if ( !registrationEnabled )
        {
            throw new ForbiddenException( "Cadastro desativado. Peca uma conta ao administrador" );
        }
        if ( userRepository.existsByEmail( registerUserRequestDTO.getEmail() ) )
        {
            throw new ConflictException( "E-mail ja cadastrado" );
        }

        User newUser = RegisterMapper.dtoToEntity( registerUserRequestDTO );

        // Cadastro publico sempre cria USER; ADMIN so e concedido por outro admin ou pelo admin inicial (ADMIN_EMAIL)
        newUser.setRole( Role.USER );

        newUser.setPassword( passwordEncoder.encode( registerUserRequestDTO.getPassword() ) );

        userRepository.save( newUser );

        return RegisterMapper.entityToDto( newUser );
    }
}
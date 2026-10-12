package com.homelab.homepage.domain.user.service;

import com.homelab.homepage.common.config.TokenConfig;
import com.homelab.homepage.domain.user.dto.request.LoginRequestDTO;
import com.homelab.homepage.domain.user.dto.request.RegisterUserRequestDTO;
import com.homelab.homepage.domain.user.dto.response.LoginResponseDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.mapper.RegisterMapper;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

        return new LoginResponseDTO( token );
    }

    /**
     * Register
     * @param registerUserRequestDTO
     * @return
     */
    public RegisterUserResponseDTO register( RegisterUserRequestDTO registerUserRequestDTO )
    {
        User newUser = RegisterMapper.dtoToEntity( registerUserRequestDTO );

        newUser.setPassword( passwordEncoder.encode( registerUserRequestDTO.getPassword() ) );

        userRepository.save( newUser );

        return RegisterMapper.entityToDto( newUser );
    }
}
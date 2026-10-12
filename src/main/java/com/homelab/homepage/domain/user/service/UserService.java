package com.homelab.homepage.domain.user.service;

import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.user.dto.response.UserResponseDTO;
import com.homelab.homepage.domain.user.mapper.UserMapper;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService
{

    private final UserRepository userRepository;

    public UserService( UserRepository userRepository )
    {
        this.userRepository = userRepository;
    }

    @Transactional( readOnly = true )
    public List<UserResponseDTO> listUsers()
    {
        return userRepository.findAll().stream()
                .map( UserMapper::toResponse )
                .toList();
    }

    public UserResponseDTO updateRole( Long userId, Role newRole )
    {
        User user = userRepository.findById( userId )
                .orElseThrow( () -> new ResourceNotFoundException( "Usuario nao encontrado" ) );

        boolean losingAdmin = user.getRole() == Role.ADMIN && newRole != Role.ADMIN;

        if ( losingAdmin && userRepository.countByRole( Role.ADMIN ) <= 1 )
        {
            throw new ConflictException( "Deve existir ao menos um administrador" );
        }

        user.setRole( newRole );
        return UserMapper.toResponse( user );
    }
}

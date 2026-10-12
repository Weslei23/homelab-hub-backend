package com.homelab.homepage.domain.user.controller;

import com.homelab.homepage.domain.user.dto.request.UpdateUserRoleRequestDTO;
import com.homelab.homepage.domain.user.dto.response.UserResponseDTO;
import com.homelab.homepage.domain.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Gestao de usuarios e roles. Todas as rotas exigem ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping( "/api/v1/users" )
public class UserController
{

    private final UserService userService;

    public UserController( UserService userService )
    {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponseDTO> listUsers()
    {
        return userService.listUsers();
    }

    @PutMapping( "/{id}/role" )
    public UserResponseDTO updateRole( @PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequestDTO request )
    {
        return userService.updateRole( id, request.role() );
    }
}

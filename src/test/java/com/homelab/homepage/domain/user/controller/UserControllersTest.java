package com.homelab.homepage.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.GlobalExceptionHandler;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.user.dto.response.LoginResponseDTO;
import com.homelab.homepage.domain.user.dto.response.RegisterUserResponseDTO;
import com.homelab.homepage.domain.user.dto.response.UserResponseDTO;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.service.AuthService;
import com.homelab.homepage.domain.user.service.UserService;

class UserControllersTest
{
    private static MockHttpServletRequestBuilder json( MockHttpServletRequestBuilder builder, String body )
    {
        return builder.contentType( MediaType.APPLICATION_JSON ).content( body );
    }

    @Nested
    class Auth
    {
        private AuthService authService;
        private MockMvc mvc;

        @BeforeEach
        void setUp()
        {
            authService = mock( AuthService.class );
            AuthController controller = new AuthController();
            ReflectionTestUtils.setField( controller, "authService", authService );
            mvc = MockMvcBuilders.standaloneSetup( controller )
                    .setControllerAdvice( new GlobalExceptionHandler() )
                    .build();
        }

        @Test
        void login() throws Exception
        {
            when( authService.login( any() ) )
                    .thenReturn( new LoginResponseDTO( "jwt", "ana", "ana@lab.local", Role.ADMIN ) );

            mvc.perform( json( post( "/api/v1/auth/login" ), "{\"email\":\"ana@lab.local\",\"password\":\"x\"}" ) )
                    .andExpect( status().isOk() )
                    .andExpect( jsonPath( "$.token" ).value( "jwt" ) )
                    .andExpect( jsonPath( "$.username" ).value( "ana" ) )
                    .andExpect( jsonPath( "$.role" ).value( "ADMIN" ) );
        }

        @Test
        void loginSemEmailDevolve400() throws Exception
        {
            mvc.perform( json( post( "/api/v1/auth/login" ), "{\"password\":\"x\"}" ) )
                    .andExpect( status().isBadRequest() )
                    .andExpect( jsonPath( "$.message" ).value( "argument 'email' must not be null or empty." ) );
            verify( authService, never() ).login( any() );
        }

        @Test
        void loginComSenhaErradaDevolve401() throws Exception
        {
            when( authService.login( any() ) ).thenThrow( new BadCredentialsException( "Bad credentials" ) );

            mvc.perform( json( post( "/api/v1/auth/login" ), "{\"email\":\"ana@lab.local\",\"password\":\"errada\"}" ) )
                    .andExpect( status().isUnauthorized() )
                    .andExpect( jsonPath( "$.message" ).value( "E-mail ou senha invalidos" ) );
        }

        @Test
        void cadastro() throws Exception
        {
            when( authService.register( any() ) ).thenReturn( new RegisterUserResponseDTO( "bia", "bia@lab.local" ) );

            mvc.perform( json( post( "/api/v1/auth/register" ),
                    "{\"username\":\"bia\",\"email\":\"bia@lab.local\",\"password\":\"x\"}" ) )
                    .andExpect( status().isCreated() )
                    .andExpect( jsonPath( "$.username" ).value( "bia" ) )
                    .andExpect( jsonPath( "$.password" ).doesNotExist() );
        }

        @Test
        void cadastroSemNomeDevolve400() throws Exception
        {
            mvc.perform( json( post( "/api/v1/auth/register" ), "{\"email\":\"bia@lab.local\",\"password\":\"x\"}" ) )
                    .andExpect( status().isBadRequest() )
                    .andExpect( jsonPath( "$.message" ).value( "argument 'username' must not be null or empty." ) );
        }

        @Test
        void cadastroComEmailRepetidoDevolve409() throws Exception
        {
            when( authService.register( any() ) ).thenThrow( new ConflictException( "E-mail ja cadastrado" ) );

            mvc.perform( json( post( "/api/v1/auth/register" ),
                    "{\"username\":\"bia\",\"email\":\"bia@lab.local\",\"password\":\"x\"}" ) )
                    .andExpect( status().isConflict() )
                    .andExpect( jsonPath( "$.message" ).value( "E-mail ja cadastrado" ) );
        }
    }

    @Nested
    class Users
    {
        private UserService userService;
        private MockMvc mvc;

        @BeforeEach
        void setUp()
        {
            userService = mock( UserService.class );
            mvc = MockMvcBuilders.standaloneSetup( new UserController( userService ) )
                    .setControllerAdvice( new GlobalExceptionHandler() )
                    .build();
        }

        @Test
        void listaUsuarios() throws Exception
        {
            when( userService.listUsers() ).thenReturn( List.of(
                    new UserResponseDTO( 1L, "ana", "ana@lab.local", Role.ADMIN ) ) );

            mvc.perform( get( "/api/v1/users" ) )
                    .andExpect( status().isOk() )
                    .andExpect( jsonPath( "$[0].email" ).value( "ana@lab.local" ) )
                    .andExpect( jsonPath( "$[0].role" ).value( "ADMIN" ) )
                    .andExpect( jsonPath( "$[0].password" ).doesNotExist() );
        }

        @Test
        void alteraRole() throws Exception
        {
            when( userService.updateRole( 2L, Role.ADMIN ) )
                    .thenReturn( new UserResponseDTO( 2L, "bia", "bia@lab.local", Role.ADMIN ) );

            mvc.perform( json( put( "/api/v1/users/2/role" ), "{\"role\":\"ADMIN\"}" ) )
                    .andExpect( status().isOk() )
                    .andExpect( jsonPath( "$.role" ).value( "ADMIN" ) );
        }

        @Test
        void roleObrigatoria() throws Exception
        {
            mvc.perform( json( put( "/api/v1/users/2/role" ), "{}" ) )
                    .andExpect( status().isBadRequest() )
                    .andExpect( jsonPath( "$.message" ).value( "A role e obrigatoria (USER ou ADMIN)" ) );
        }

        @Test
        void roleInexistenteDevolve400() throws Exception
        {
            mvc.perform( json( put( "/api/v1/users/2/role" ), "{\"role\":\"ROOT\"}" ) )
                    .andExpect( status().isBadRequest() );
            verify( userService, never() ).updateRole( any(), any() );
        }

        @Test
        void ultimoAdminDevolve409() throws Exception
        {
            when( userService.updateRole( 1L, Role.USER ) )
                    .thenThrow( new ConflictException( "Deve existir ao menos um administrador" ) );

            mvc.perform( json( put( "/api/v1/users/1/role" ), "{\"role\":\"USER\"}" ) )
                    .andExpect( status().isConflict() )
                    .andExpect( jsonPath( "$.message" ).value( "Deve existir ao menos um administrador" ) );
        }

        @Test
        void usuarioInexistenteDevolve404() throws Exception
        {
            when( userService.updateRole( 99L, Role.USER ) )
                    .thenThrow( new ResourceNotFoundException( "Usuario nao encontrado" ) );

            mvc.perform( json( put( "/api/v1/users/99/role" ), "{\"role\":\"USER\"}" ) )
                    .andExpect( status().isNotFound() );
        }
    }
}

package com.homelab.homepage.common.exceptions;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler
{

    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<Map<String, String>> handleInvalidRequest( MethodArgumentNotValidException exception )
    {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map( fieldError -> fieldError.getDefaultMessage() )
                .findFirst()
                .orElse( "Requisicao invalida" );
        return ResponseEntity.status( HttpStatus.BAD_REQUEST ).body( Map.of( "message", message ) );
    }

    @ExceptionHandler( ResourceNotFoundException.class )
    public ResponseEntity<Map<String, String>> handleNotFound( ResourceNotFoundException exception )
    {
        return ResponseEntity.status( HttpStatus.NOT_FOUND ).body( Map.of( "message", exception.getMessage() ) );
    }

    @ExceptionHandler( ConflictException.class )
    public ResponseEntity<Map<String, String>> handleConflict( ConflictException exception )
    {
        return ResponseEntity.status( HttpStatus.CONFLICT ).body( Map.of( "message", exception.getMessage() ) );
    }

    /** Login com e-mail ou senha incorretos. */
    @ExceptionHandler( AuthenticationException.class )
    public ResponseEntity<Map<String, String>> handleAuthentication( AuthenticationException exception )
    {
        return ResponseEntity.status( HttpStatus.UNAUTHORIZED ).body( Map.of( "message", "E-mail ou senha invalidos" ) );
    }
}

package com.homelab.homepage.common.exceptions;

import com.homelab.homepage.domain.media.client.MediaException;
import com.homelab.homepage.domain.music.client.DowntifyException;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler( BadRequestException.class )
    public ResponseEntity<Map<String, String>> handleBadRequest( BadRequestException exception )
    {
        return ResponseEntity.status( HttpStatus.BAD_REQUEST ).body( Map.of( "message", exception.getMessage() ) );
    }

    @ExceptionHandler( ForbiddenException.class )
    public ResponseEntity<Map<String, String>> handleForbidden( ForbiddenException exception )
    {
        return ResponseEntity.status( HttpStatus.FORBIDDEN ).body( Map.of( "message", exception.getMessage() ) );
    }

    /** Muitas tentativas de login: informa quando tentar de novo (cabecalho Retry-After). */
    @ExceptionHandler( TooManyRequestsException.class )
    public ResponseEntity<Map<String, String>> handleTooManyRequests( TooManyRequestsException exception )
    {
        return ResponseEntity.status( HttpStatus.TOO_MANY_REQUESTS )
                .header( "Retry-After", String.valueOf( exception.getRetryAfterSeconds() ) )
                .body( Map.of( "message", exception.getMessage() ) );
    }

    /** Falha num servico externo (Downtify): o problema nao e do dashboard nem do pedido. */
    @ExceptionHandler( DowntifyException.class )
    public ResponseEntity<Map<String, String>> handleDowntify( DowntifyException exception )
    {
        return ResponseEntity.status( HttpStatus.BAD_GATEWAY ).body( Map.of( "message", exception.getMessage() ) );
    }

    /** Falha no Sonarr, Radarr ou Jellyfin. */
    @ExceptionHandler( MediaException.class )
    public ResponseEntity<Map<String, String>> handleMedia( MediaException exception )
    {
        return ResponseEntity.status( HttpStatus.BAD_GATEWAY ).body( Map.of( "message", exception.getMessage() ) );
    }

    /** Parametro obrigatorio ausente na URL (ex.: ?term= na busca). */
    @ExceptionHandler( MissingServletRequestParameterException.class )
    public ResponseEntity<Map<String, String>> handleMissingParameter( MissingServletRequestParameterException exception )
    {
        return ResponseEntity.status( HttpStatus.BAD_REQUEST )
                .body( Map.of( "message", "Parametro obrigatorio ausente: " + exception.getParameterName() ) );
    }

    /** Login com e-mail ou senha incorretos. */
    @ExceptionHandler( AuthenticationException.class )
    public ResponseEntity<Map<String, String>> handleAuthentication( AuthenticationException exception )
    {
        return ResponseEntity.status( HttpStatus.UNAUTHORIZED ).body( Map.of( "message", "E-mail ou senha invalidos" ) );
    }
}

package com.homelab.homepage.common.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest
{
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MethodArgumentNotValidException validationError( FieldError... errors ) throws Exception
    {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult( new Object(), "request" );
        for ( FieldError error : errors )
        {
            result.addError( error );
        }
        return new MethodArgumentNotValidException( new MethodParameter( Object.class.getMethod( "toString" ), -1 ), result );
    }

    @Test
    void validacaoDevolve400ComAPrimeiraMensagem() throws Exception
    {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidRequest( validationError(
                new FieldError( "request", "name", "O nome e obrigatorio" ),
                new FieldError( "request", "url", "A URL e obrigatoria" ) ) );

        assertThat( response.getStatusCode() ).isEqualTo( HttpStatus.BAD_REQUEST );
        assertThat( response.getBody() ).containsEntry( "message", "O nome e obrigatorio" );
    }

    @Test
    void validacaoSemErroDeCampoUsaMensagemPadrao() throws Exception
    {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidRequest( validationError() );

        assertThat( response.getBody() ).containsEntry( "message", "Requisicao invalida" );
    }

    @Test
    void naoEncontradoDevolve404()
    {
        ResponseEntity<Map<String, String>> response = handler.handleNotFound( new ResourceNotFoundException( "Card nao encontrado" ) );

        assertThat( response.getStatusCode() ).isEqualTo( HttpStatus.NOT_FOUND );
        assertThat( response.getBody() ).containsEntry( "message", "Card nao encontrado" );
    }

    @Test
    void conflitoDevolve409()
    {
        ResponseEntity<Map<String, String>> response = handler.handleConflict( new ConflictException( "E-mail ja cadastrado" ) );

        assertThat( response.getStatusCode() ).isEqualTo( HttpStatus.CONFLICT );
        assertThat( response.getBody() ).containsEntry( "message", "E-mail ja cadastrado" );
    }

    @Test
    void pedidoInvalidoDevolve400()
    {
        ResponseEntity<Map<String, String>> response = handler.handleBadRequest( new BadRequestException( "Use um link do Spotify" ) );

        assertThat( response.getStatusCode() ).isEqualTo( HttpStatus.BAD_REQUEST );
        assertThat( response.getBody() ).containsEntry( "message", "Use um link do Spotify" );
    }

    @Test
    void falhaNoDowntifyDevolve502()
    {
        ResponseEntity<Map<String, String>> response = handler.handleDowntify(
                new com.homelab.homepage.domain.music.client.DowntifyException( "Downtify indisponivel em http://x" ) );

        assertThat( response.getStatusCode() ).isEqualTo( HttpStatus.BAD_GATEWAY );
        assertThat( response.getBody() ).containsEntry( "message", "Downtify indisponivel em http://x" );
    }

    @Test
    void falhaDeAutenticacaoDevolve401SemRevelarOMotivo()
    {
        // A mensagem e sempre a mesma: nao revela se o e-mail existe ou se a conta esta desativada
        assertThat( handler.handleAuthentication( new BadCredentialsException( "Bad credentials" ) ).getBody() )
                .containsEntry( "message", "E-mail ou senha invalidos" );
        ResponseEntity<Map<String, String>> disabled = handler.handleAuthentication( new DisabledException( "conta desativada" ) );
        assertThat( disabled.getStatusCode() ).isEqualTo( HttpStatus.UNAUTHORIZED );
        assertThat( disabled.getBody() ).containsEntry( "message", "E-mail ou senha invalidos" );
    }
}

package com.homelab.homepage.common.exceptions;

/** Dado enviado pelo cliente e valido no formato, mas que nao pode ser usado (ex.: link que nao e do Spotify). */
public class BadRequestException extends RuntimeException
{

    public BadRequestException( String message )
    {
        super( message );
    }
}

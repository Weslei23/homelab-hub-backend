package com.homelab.homepage.common.exceptions;

/** Acao desativada ou nao permitida (HTTP 403). */
public class ForbiddenException extends RuntimeException
{
    public ForbiddenException( String message )
    {
        super( message );
    }
}

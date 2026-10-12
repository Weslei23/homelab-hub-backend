package com.homelab.homepage.common.exceptions;

/** Regra de negocio violada por conflito com o estado atual (ex.: e-mail ja cadastrado). */
public class ConflictException extends RuntimeException
{

    public ConflictException( String message )
    {
        super( message );
    }
}

package com.homelab.homepage.domain.music.client;

/** Falha ao falar com o Downtify (fora do ar, token recusado, resposta inesperada). Vira HTTP 502. */
public class DowntifyException extends RuntimeException
{

    public DowntifyException( String message )
    {
        super( message );
    }

    public DowntifyException( String message, Throwable cause )
    {
        super( message, cause );
    }
}

package com.homelab.homepage.domain.media.client;

/** Falha ao falar com o Sonarr, o Radarr ou o Jellyfin. A mensagem vai direto para a tela. */
public class MediaException extends RuntimeException
{
    public MediaException( String message )
    {
        super( message );
    }

    public MediaException( String message, Throwable cause )
    {
        super( message, cause );
    }
}

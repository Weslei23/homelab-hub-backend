package com.homelab.homepage.domain.music.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Objetos de entrada e saida da API /api/music. */
public final class MusicDtos
{
    private MusicDtos()
    {
    }

    public record DownloadRequest(
            @NotBlank( message = "Cole um link do Spotify" )
            @Size( max = 500, message = "Link muito longo" )
            String url )
    {
    }

    /** @param type faixa, album ou playlist */
    public record DownloadResponse( int queued, String type )
    {
    }

    /** @param status queued, downloading, done ou error (como o Downtify informa) */
    public record QueueItemResponse( String title, String artists, String status, Double progress, String message )
    {
    }

    public record PlaylistRequest(
            @NotBlank( message = "Cole o link da playlist do Spotify" )
            @Size( max = 500, message = "Link muito longo" )
            String url,
            @Size( max = 100, message = "O nome pode ter no maximo 100 caracteres" )
            String name )
    {
    }

    public record PlaylistUpdateRequest(
            @NotNull( message = "Informe se a sincronizacao automatica fica ligada" )
            Boolean enabled )
    {
    }

    public record PlaylistResponse( Long id, String name, String url, boolean enabled, int knownTracks,
            Instant lastSyncAt, Boolean lastSyncOk, String lastSyncMessage )
    {
    }

    /**
     * @param configured  DOWNTIFY_URL definido
     * @param reachable   o Downtify respondeu agora
     * @param syncInterval intervalo da sincronizacao automatica (ex.: "6h")
     */
    public record MusicStatusResponse( boolean configured, boolean reachable, String version, String syncInterval )
    {
    }
}

package com.homelab.homepage.domain.media.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Objetos de entrada e saida da API /api/media. */
public final class MediaDtos
{
    private MediaDtos()
    {
    }

    /**
     * Situacao de um servico.
     *
     * @param configured URL definida no backend
     * @param reachable  respondeu agora (e aceitou a API key)
     * @param url        endereco do servico, para a tela oferecer um link
     */
    public record ServiceInfo( boolean configured, boolean reachable, String version, String url )
    {
        public static ServiceInfo notConfigured()
        {
            return new ServiceInfo( false, false, null, null );
        }
    }

    public record Services( ServiceInfo sonarr, ServiceInfo radarr, ServiceInfo jellyfin )
    {
    }

    /**
     * Download em andamento no Sonarr/Radarr.
     *
     * @param kind     episode ou movie
     * @param state    queued, downloading, paused, importing, warning ou failed
     * @param progress 0 a 100
     * @param timeLeft tempo restante como o *arr informa (ex.: "00:12:34"), ou null
     * @param message  aviso do *arr quando algo esta errado
     */
    public record QueueItem( String kind, String title, String detail, String state, double progress, String timeLeft,
            String message )
    {
    }

    /**
     * Proximo episodio ou lancamento de filme.
     *
     * @param kind    episode ou movie
     * @param hasFile ja foi baixado
     */
    public record UpcomingItem( String kind, String title, String detail, Instant date, boolean hasFile )
    {
    }

    /** Item adicionado recentemente ao Jellyfin. */
    public record RecentItem( String id, String kind, String title, String detail, String imageUrl, String link,
            Instant addedAt )
    {
    }

    /** Alguem assistindo algo no Jellyfin agora. */
    public record NowPlayingItem( String user, String title, String detail, String device, double progress,
            boolean paused, String imageUrl )
    {
    }

    /**
     * Tudo que o painel de midia mostra, numa chamada so. Uma secao que falhou fica vazia
     * e o motivo entra em {@code errors}; as demais continuam aparecendo.
     */
    public record MediaOverview( Services services, List<QueueItem> queue, List<UpcomingItem> upcoming,
            List<RecentItem> recent, List<NowPlayingItem> nowPlaying, List<String> errors )
    {
    }

    /**
     * Resultado da busca de filme ou serie.
     *
     * @param id        tmdbId (filme) ou tvdbId (serie): e o que vai no pedido
     * @param inLibrary ja esta no Radarr/Sonarr
     */
    public record SearchResult( String type, long id, String title, Integer year, String overview, String posterUrl,
            boolean inLibrary )
    {
    }

    public record MediaRequest(
            @NotNull( message = "Informe se e filme ou serie" )
            @Pattern( regexp = "movie|series", message = "O tipo deve ser movie ou series" )
            String type,
            @NotNull( message = "Escolha um resultado da busca" )
            @Positive( message = "Escolha um resultado da busca" )
            Long id,
            // So para series: all, future, firstSeason, lastSeason ou pilot (padrao: all)
            @Pattern( regexp = "all|future|firstSeason|lastSeason|pilot", message = "Opcao de episodios invalida" )
            String monitor )
    {
    }

    /** Pedido aceito: o *arr ja comecou a procurar. */
    public record MediaRequestResponse( String type, String title, String message )
    {
    }
}

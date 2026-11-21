package com.homelab.homepage.domain.music.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.music.client.DowntifyClient;
import com.homelab.homepage.domain.music.client.DowntifyException;
import com.homelab.homepage.domain.music.client.SpotifyUrl;
import com.homelab.homepage.domain.music.dto.MusicDtos.DownloadResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.MusicStatusResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistRequest;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.QueueItemResponse;
import com.homelab.homepage.domain.music.model.MusicPlaylist;
import com.homelab.homepage.domain.music.repository.MusicPlaylistRepository;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;

import tools.jackson.databind.JsonNode;

/**
 * Downloads de musica pelo Downtify:
 * - download imediato a partir de um link (faixa, album ou playlist);
 * - playlists acompanhadas: a cada ciclo, so as faixas que ainda nao foram enviadas vao para a fila.
 */
@Service
public class MusicService
{
    private static final Logger log = LoggerFactory.getLogger( MusicService.class );

    private final DowntifyClient downtify;
    private final MusicPlaylistRepository playlistRepository;
    private final DiscordNotifier notifier;
    private final String syncInterval;
    /** Impede que a sincronizacao agendada e a manual rodem ao mesmo tempo (baixaria faixas em dobro). */
    private final ReentrantLock syncLock = new ReentrantLock();

    public MusicService( DowntifyClient downtify, MusicPlaylistRepository playlistRepository, DiscordNotifier notifier,
            @Value( "${app.downtify.sync-interval:6h}" ) String syncInterval )
    {
        this.downtify = downtify;
        this.playlistRepository = playlistRepository;
        this.notifier = notifier;
        this.syncInterval = syncInterval;
    }

    public MusicStatusResponse status()
    {
        if ( !downtify.isConfigured() )
        {
            return new MusicStatusResponse( false, false, null, syncInterval, null );
        }
        String version = downtify.version().orElse( null );
        return new MusicStatusResponse( true, version != null, version, syncInterval, downtify.baseUrl() );
    }

    // ---- download imediato ----

    public DownloadResponse download( String rawUrl )
    {
        SpotifyUrl url = SpotifyUrl.parse( rawUrl );
        List<JsonNode> songs = distinct( downtify.resolve( url.url() ) );
        if ( songs.isEmpty() )
        {
            throw new BadRequestException( "Nenhuma faixa encontrada nesse link" );
        }
        downtify.downloadBatch( songs, url.type() == SpotifyUrl.Type.PLAYLIST ? url.url() : null );
        return new DownloadResponse( songs.size(), url.type().label() );
    }

    public List<QueueItemResponse> queue()
    {
        return downtify.queue().stream().map( MusicService::toQueueItem ).toList();
    }

    // ---- playlists acompanhadas ----

    public List<PlaylistResponse> listPlaylists()
    {
        return playlistRepository.findAllByOrderByNameAsc().stream().map( MusicService::toResponse ).toList();
    }

    /** Cadastra a playlist e ja faz a primeira sincronizacao (baixa todas as faixas atuais). */
    public PlaylistResponse addPlaylist( PlaylistRequest request )
    {
        SpotifyUrl url = SpotifyUrl.parse( request.url() );
        if ( url.type() != SpotifyUrl.Type.PLAYLIST )
        {
            throw new BadRequestException( "So playlists podem ser acompanhadas; para faixa ou album use \"Baixar\"" );
        }
        if ( playlistRepository.existsByUrl( url.url() ) )
        {
            throw new ConflictException( "Essa playlist ja esta cadastrada" );
        }
        String name = request.name() == null || request.name().isBlank() ? null : request.name().trim();
        return toResponse( withLock( () -> {
            MusicPlaylist playlist = playlistRepository.save(
                    new MusicPlaylist( name != null ? name : "Playlist " + url.id(), url.url() ) );
            return sync( playlist, name == null );
        } ) );
    }

    public PlaylistResponse setEnabled( Long id, boolean enabled )
    {
        MusicPlaylist playlist = findOrFail( id );
        playlist.setEnabled( enabled );
        return toResponse( playlistRepository.save( playlist ) );
    }

    public void deletePlaylist( Long id )
    {
        playlistRepository.delete( findOrFail( id ) );
    }

    public PlaylistResponse syncNow( Long id )
    {
        MusicPlaylist playlist = findOrFail( id );
        return toResponse( withLock( () -> sync( playlist, false ) ) );
    }

    /** Sincronizacao automatica de todas as playlists ligadas. */
    @Scheduled( initialDelayString = "${app.downtify.sync-initial-delay:2m}", fixedDelayString = "${app.downtify.sync-interval:6h}" )
    public void syncAll()
    {
        if ( !downtify.isConfigured() || !syncLock.tryLock() )
        {
            return;
        }
        try
        {
            for ( MusicPlaylist playlist : playlistRepository.findAllByEnabledTrueOrderByNameAsc() )
            {
                try
                {
                    sync( playlist, false );
                }
                catch ( RuntimeException e )
                {
                    log.warn( "Falha ao sincronizar a playlist {}: {}", playlist.getName(), e.getMessage() );
                }
            }
        }
        finally
        {
            syncLock.unlock();
        }
    }

    /** Envia ao Downtify so as faixas que ainda nao foram enviadas e registra o resultado. */
    private MusicPlaylist sync( MusicPlaylist playlist, boolean adoptName )
    {
        try
        {
            List<JsonNode> songs = distinct( downtify.resolve( playlist.getUrl() ) );
            if ( adoptName && !songs.isEmpty() )
            {
                String listName = DowntifyClient.text( songs.getFirst(), "list_name" );
                if ( listName != null && !listName.isBlank() )
                {
                    playlist.setName( listName.length() > 100 ? listName.substring( 0, 100 ) : listName );
                }
            }
            List<JsonNode> fresh = songs.stream()
                    .filter( song -> !playlist.getKnownSongs().contains( DowntifyClient.songKey( song ) ) )
                    .toList();

            if ( fresh.isEmpty() )
            {
                playlist.recordSync( true, "Nenhuma faixa nova" );
            }
            else
            {
                downtify.downloadBatch( fresh, playlist.getUrl() );
                fresh.forEach( song -> playlist.getKnownSongs().add( DowntifyClient.songKey( song ) ) );
                playlist.recordSync( true, fresh.size() + " faixa(s) nova(s) enviada(s) ao Downtify" );
                notifier.notifyNewMusic( playlist.getName(), playlist.getUrl(), fresh.stream().map( MusicService::label ).toList() );
            }
        }
        catch ( DowntifyException e )
        {
            playlist.recordSync( false, e.getMessage() );
        }
        return playlistRepository.save( playlist );
    }

    private MusicPlaylist withLock( java.util.function.Supplier<MusicPlaylist> action )
    {
        if ( !syncLock.tryLock() )
        {
            throw new ConflictException( "Ja existe uma sincronizacao em andamento; tente de novo em instantes" );
        }
        try
        {
            return action.get();
        }
        finally
        {
            syncLock.unlock();
        }
    }

    private MusicPlaylist findOrFail( Long id )
    {
        return playlistRepository.findById( id )
                .orElseThrow( () -> new ResourceNotFoundException( "Playlist nao encontrada" ) );
    }

    /** Remove faixas repetidas (a mesma musica duas vezes na playlist). */
    private static List<JsonNode> distinct( List<JsonNode> songs )
    {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        songs.forEach( song -> byKey.putIfAbsent( DowntifyClient.songKey( song ), song ) );
        return List.copyOf( byKey.values() );
    }

    private static String label( JsonNode song )
    {
        String artists = DowntifyClient.artists( song );
        String name = DowntifyClient.text( song, "name" );
        return artists == null ? name : name + " — " + artists;
    }

    private static QueueItemResponse toQueueItem( JsonNode job )
    {
        JsonNode song = job.get( "song" );
        JsonNode progress = job.get( "progress" );
        return new QueueItemResponse(
                DowntifyClient.text( song, "name" ),
                DowntifyClient.artists( song ),
                DowntifyClient.text( job, "status" ),
                progress == null || progress.isNull() ? null : progress.asDouble(),
                DowntifyClient.text( job, "message" ) );
    }

    private static PlaylistResponse toResponse( MusicPlaylist playlist )
    {
        return new PlaylistResponse( playlist.getId(), playlist.getName(), playlist.getUrl(), playlist.isEnabled(),
                playlist.getKnownSongs().size(), playlist.getLastSyncAt(), playlist.getLastSyncOk(), playlist.getLastSyncMessage() );
    }
}

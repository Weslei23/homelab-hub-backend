package com.homelab.homepage.domain.media.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.domain.media.client.ArrClient;
import com.homelab.homepage.domain.media.client.ArrClients;
import com.homelab.homepage.domain.media.client.JellyfinClient;
import com.homelab.homepage.domain.media.client.MediaException;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaOverview;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequest;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequestResponse;
import com.homelab.homepage.domain.media.dto.MediaDtos.NowPlayingItem;
import com.homelab.homepage.domain.media.dto.MediaDtos.QueueItem;
import com.homelab.homepage.domain.media.dto.MediaDtos.RecentItem;
import com.homelab.homepage.domain.media.dto.MediaDtos.SearchResult;
import com.homelab.homepage.domain.media.dto.MediaDtos.ServiceInfo;
import com.homelab.homepage.domain.media.dto.MediaDtos.Services;
import com.homelab.homepage.domain.media.dto.MediaDtos.UpcomingItem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Painel de midia: downloads do Sonarr/Radarr, proximos lancamentos, o que entrou no Jellyfin
 * e quem esta assistindo. Tambem faz os pedidos de filme/serie (busca e adiciona no *arr).
 */
@Service
public class MediaService
{
    /** Dias a frente mostrados em "proximos lancamentos". */
    static final int UPCOMING_DAYS = 14;
    static final int UPCOMING_LIMIT = 20;
    static final int RECENT_LIMIT = 16;
    static final int SEARCH_LIMIT = 12;
    private static final int OVERVIEW_TEXT_LIMIT = 300;

    private final ArrClient sonarr;
    private final ArrClient radarr;
    private final JellyfinClient jellyfin;
    private final String qualityProfile;
    private final Clock clock;

    @Autowired
    public MediaService( ArrClients arr, JellyfinClient jellyfin,
            @Value( "${app.media.quality-profile:HD-1080p}" ) String qualityProfile )
    {
        this( arr, jellyfin, qualityProfile, Clock.systemDefaultZone() );
    }

    MediaService( ArrClients arr, JellyfinClient jellyfin, String qualityProfile, Clock clock )
    {
        this.sonarr = arr.sonarr();
        this.radarr = arr.radarr();
        this.jellyfin = jellyfin;
        this.qualityProfile = qualityProfile;
        this.clock = clock;
    }

    // ---- painel ----

    /** Resultado parcial de um servico. */
    private record Part( ServiceInfo info, List<QueueItem> queue, List<UpcomingItem> upcoming, List<RecentItem> recent,
            List<NowPlayingItem> nowPlaying, List<String> errors )
    {
    }

    /** Consulta os tres servicos em paralelo: um servico fora do ar nao atrasa nem esconde os outros. */
    public MediaOverview overview()
    {
        Part series;
        Part movies;
        Part player;
        try ( ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor() )
        {
            Future<Part> s = executor.submit( () -> arrPart( sonarr ) );
            Future<Part> m = executor.submit( () -> arrPart( radarr ) );
            Future<Part> j = executor.submit( this::jellyfinPart );
            series = join( s );
            movies = join( m );
            player = join( j );
        }

        List<QueueItem> queue = new ArrayList<>( series.queue() );
        queue.addAll( movies.queue() );

        List<UpcomingItem> upcoming = new ArrayList<>( series.upcoming() );
        upcoming.addAll( movies.upcoming() );
        upcoming.sort( Comparator.comparing( UpcomingItem::date ) );

        List<String> errors = new ArrayList<>( series.errors() );
        errors.addAll( movies.errors() );
        errors.addAll( player.errors() );

        return new MediaOverview( new Services( series.info(), movies.info(), player.info() ), queue,
                upcoming.stream().limit( UPCOMING_LIMIT ).toList(), player.recent(), player.nowPlaying(), errors );
    }

    /** Mensagem mostrada a quem nao e ADMIN quando algum servico falha. */
    static final String VIEWER_ERROR = "Algum servico de midia esta indisponivel no momento";

    /**
     * Versao do painel para quem nao e ADMIN: sem os enderecos do Sonarr/Radarr (paginas de administracao)
     * e sem o detalhe das falhas, que expoe IPs e portas internas. O Jellyfin continua com link: e onde se assiste.
     */
    public static MediaOverview forViewer( MediaOverview overview )
    {
        Services s = overview.services();
        Services hidden = new Services( withoutUrl( s.sonarr() ), withoutUrl( s.radarr() ), s.jellyfin() );
        List<String> errors = overview.errors().isEmpty() ? List.of() : List.of( VIEWER_ERROR );
        return new MediaOverview( hidden, overview.queue(), overview.upcoming(), overview.recent(), overview.nowPlaying(),
                errors );
    }

    private static ServiceInfo withoutUrl( ServiceInfo info )
    {
        return new ServiceInfo( info.configured(), info.reachable(), info.version(), null );
    }

    private Part arrPart( ArrClient client )
    {
        if ( !client.isConfigured() )
        {
            return new Part( ServiceInfo.notConfigured(), List.of(), List.of(), List.of(), List.of(), List.of() );
        }
        List<String> errors = new ArrayList<>();
        String version = attempt( client::version, errors );
        if ( version == null )
        {
            // Fora do ar ou chave errada: nao adianta tentar o resto
            return new Part( new ServiceInfo( true, false, null, client.baseUrl() ), List.of(), List.of(), List.of(),
                    List.of(), errors );
        }
        boolean episodes = client.kind() == ArrClient.Kind.SONARR;
        LocalDate today = LocalDate.now( clock );
        LocalDate end = today.plusDays( UPCOMING_DAYS );

        List<JsonNode> queue = attempt( client::queue, errors );
        List<JsonNode> calendar = attempt( () -> client.calendar( today, end ), errors );

        List<QueueItem> queueItems = queue == null ? List.of() : queue.stream().map( r -> toQueueItem( r, episodes ) ).toList();
        List<UpcomingItem> upcoming = new ArrayList<>();
        if ( calendar != null )
        {
            Instant from = today.atStartOfDay( clock.getZone() ).toInstant();
            Instant to = end.plusDays( 1 ).atStartOfDay( clock.getZone() ).toInstant();
            for ( JsonNode item : calendar )
            {
                UpcomingItem upcomingItem = episodes ? toUpcomingEpisode( item ) : toUpcomingMovie( item, from, to );
                if ( upcomingItem != null )
                {
                    upcoming.add( upcomingItem );
                }
            }
        }
        return new Part( new ServiceInfo( true, true, version, client.baseUrl() ), queueItems, upcoming, List.of(),
                List.of(), errors );
    }

    private Part jellyfinPart()
    {
        if ( !jellyfin.isConfigured() )
        {
            return new Part( ServiceInfo.notConfigured(), List.of(), List.of(), List.of(), List.of(), List.of() );
        }
        List<String> errors = new ArrayList<>();
        String version = attempt( jellyfin::version, errors );
        if ( version == null )
        {
            return new Part( new ServiceInfo( true, false, null, jellyfin.baseUrl() ), List.of(), List.of(), List.of(),
                    List.of(), errors );
        }
        List<JsonNode> latest = attempt( () -> jellyfin.latest( RECENT_LIMIT ), errors );
        List<JsonNode> sessions = attempt( jellyfin::sessions, errors );

        List<RecentItem> recent = latest == null ? List.of() : latest.stream().map( this::toRecentItem ).toList();
        List<NowPlayingItem> nowPlaying = sessions == null ? List.of()
                : sessions.stream().filter( s -> s.hasNonNull( "NowPlayingItem" ) ).map( this::toNowPlaying ).toList();
        return new Part( new ServiceInfo( true, true, version, jellyfin.baseUrl() ), List.of(), List.of(), recent,
                nowPlaying, errors );
    }

    // ---- pedidos ----

    public List<SearchResult> search( String type, String term )
    {
        String query = term == null ? "" : term.trim();
        if ( query.length() < 2 )
        {
            throw new BadRequestException( "Digite pelo menos 2 letras para buscar" );
        }
        boolean movie = isMovie( type );
        ArrClient client = clientFor( movie );
        return client.lookup( query ).stream()
                .map( item -> toSearchResult( item, movie ) )
                .filter( r -> r.id() > 0 )
                .limit( SEARCH_LIMIT )
                .toList();
    }

    public MediaRequestResponse request( MediaRequest request )
    {
        boolean movie = isMovie( request.type() );
        ArrClient client = clientFor( movie );
        JsonNode found = client.lookupById( request.id() )
                .orElseThrow( () -> new BadRequestException( "Titulo nao encontrado no " + client.kind().label() ) );
        String title = ArrClient.text( found, "title" );
        if ( found.path( "id" ).asLong( 0 ) > 0 )
        {
            throw new ConflictException( "\"" + title + "\" ja esta na biblioteca do " + client.kind().label() );
        }

        ObjectNode item = (ObjectNode) found.deepCopy();
        item.put( "qualityProfileId", qualityProfileId( client ) );
        item.put( "rootFolderPath", rootFolder( client ) );
        item.put( "monitored", true );
        ObjectNode options = item.putObject( "addOptions" );
        if ( movie )
        {
            item.put( "minimumAvailability", "released" );
            options.put( "monitor", "movieOnly" );
            options.put( "searchForMovie", true );
        }
        else
        {
            item.put( "seasonFolder", true );
            options.put( "monitor", request.monitor() == null ? "all" : request.monitor() );
            options.put( "searchForMissingEpisodes", true );
            options.put( "searchForCutoffUnmetEpisodes", false );
        }
        client.add( item );
        return new MediaRequestResponse( movie ? "movie" : "series", title,
                client.kind().label() + " adicionou \"" + title + "\" e ja esta procurando" );
    }

    private static boolean isMovie( String type )
    {
        if ( "movie".equals( type ) )
        {
            return true;
        }
        if ( "series".equals( type ) )
        {
            return false;
        }
        throw new BadRequestException( "O tipo deve ser movie ou series" );
    }

    private ArrClient clientFor( boolean movie )
    {
        ArrClient client = movie ? radarr : sonarr;
        if ( !client.isConfigured() )
        {
            throw new BadRequestException( client.kind().label() + " nao configurado no backend" );
        }
        return client;
    }

    /** Perfil de qualidade pelo nome configurado; se nao existir, o primeiro do *arr. */
    private long qualityProfileId( ArrClient client )
    {
        List<JsonNode> profiles = client.qualityProfiles();
        return profiles.stream()
                .filter( p -> qualityProfile.equalsIgnoreCase( ArrClient.text( p, "name" ) ) )
                .findFirst()
                .or( () -> profiles.stream().findFirst() )
                .map( p -> p.path( "id" ).asLong() )
                .orElseThrow( () -> new BadRequestException( "Nenhum perfil de qualidade no " + client.kind().label() ) );
    }

    private static String rootFolder( ArrClient client )
    {
        return client.rootFolders().stream()
                .map( f -> ArrClient.text( f, "path" ) )
                .filter( p -> p != null && !p.isBlank() )
                .findFirst()
                .orElseThrow( () -> new BadRequestException(
                        "Cadastre uma pasta raiz no " + client.kind().label() + " (Settings > Media Management)" ) );
    }

    // ---- conversoes (pacote: usadas nos testes) ----

    static QueueItem toQueueItem( JsonNode record, boolean episode )
    {
        String title;
        String detail;
        if ( episode )
        {
            title = firstNonNull( ArrClient.text( record.path( "series" ), "title" ), ArrClient.text( record, "title" ) );
            JsonNode ep = record.path( "episode" );
            detail = ep.isMissingNode() ? null
                    : join( episodeCode( ep.path( "seasonNumber" ).asInt( 0 ), ep.path( "episodeNumber" ).asInt( 0 ) ),
                            ArrClient.text( ep, "title" ) );
        }
        else
        {
            JsonNode movie = record.path( "movie" );
            title = firstNonNull( ArrClient.text( movie, "title" ), ArrClient.text( record, "title" ) );
            int year = movie.path( "year" ).asInt( 0 );
            detail = year > 0 ? String.valueOf( year ) : null;
        }

        double size = record.path( "size" ).asDouble( 0 );
        double left = record.path( "sizeleft" ).asDouble( 0 );
        double progress = size > 0 ? Math.round( ( size - left ) / size * 1000 ) / 10.0 : 0;
        return new QueueItem( episode ? "episode" : "movie", title, detail, queueState( record ),
                Math.max( 0, Math.min( 100, progress ) ), ArrClient.text( record, "timeleft" ), queueMessage( record ) );
    }

    static String queueState( JsonNode record )
    {
        String status = lower( ArrClient.text( record, "status" ) );
        String tracked = lower( ArrClient.text( record, "trackedDownloadState" ) );
        String health = lower( ArrClient.text( record, "trackedDownloadStatus" ) );
        if ( "failed".equals( status ) || "failed".equals( tracked ) || "failedpending".equals( tracked ) )
        {
            return "failed";
        }
        if ( "warning".equals( health ) || "error".equals( health ) || "warning".equals( status ) )
        {
            return "warning";
        }
        if ( "importpending".equals( tracked ) || "importing".equals( tracked ) || "imported".equals( tracked )
                || "completed".equals( status ) )
        {
            return "importing";
        }
        if ( "paused".equals( status ) )
        {
            return "paused";
        }
        if ( "queued".equals( status ) || "delay".equals( status ) || "downloadclientunavailable".equals( status ) )
        {
            return "queued";
        }
        return "downloading";
    }

    private static String queueMessage( JsonNode record )
    {
        String error = ArrClient.text( record, "errorMessage" );
        if ( error != null && !error.isBlank() )
        {
            return error;
        }
        for ( JsonNode status : record.path( "statusMessages" ) )
        {
            for ( JsonNode message : status.path( "messages" ) )
            {
                if ( !message.asString().isBlank() )
                {
                    return message.asString();
                }
            }
            String title = ArrClient.text( status, "title" );
            if ( title != null && !title.isBlank() )
            {
                return title;
            }
        }
        return null;
    }

    static UpcomingItem toUpcomingEpisode( JsonNode episode )
    {
        Instant date = instant( ArrClient.text( episode, "airDateUtc" ) );
        if ( date == null )
        {
            return null;
        }
        String series = ArrClient.text( episode.path( "series" ), "title" );
        String code = episodeCode( episode.path( "seasonNumber" ).asInt( 0 ), episode.path( "episodeNumber" ).asInt( 0 ) );
        return new UpcomingItem( "episode", series == null ? "Série" : series, join( code, ArrClient.text( episode, "title" ) ),
                date, episode.path( "hasFile" ).asBoolean( false ) );
    }

    /** Filme: usa o primeiro lancamento (digital, fisico ou cinema) que cai dentro da janela. */
    static UpcomingItem toUpcomingMovie( JsonNode movie, Instant from, Instant to )
    {
        Map<String, String> releases = Map.of(
                "digitalRelease", "Lançamento digital",
                "physicalRelease", "Lançamento em mídia física",
                "inCinemas", "Nos cinemas" );
        Instant chosen = null;
        String label = null;
        for ( Map.Entry<String, String> release : releases.entrySet() )
        {
            Instant date = instant( ArrClient.text( movie, release.getKey() ) );
            if ( date != null && !date.isBefore( from ) && date.isBefore( to ) && ( chosen == null || date.isBefore( chosen ) ) )
            {
                chosen = date;
                label = release.getValue();
            }
        }
        if ( chosen == null )
        {
            return null;
        }
        int year = movie.path( "year" ).asInt( 0 );
        String title = ArrClient.text( movie, "title" );
        return new UpcomingItem( "movie", year > 0 ? title + " (" + year + ")" : title, label, chosen,
                movie.path( "hasFile" ).asBoolean( false ) );
    }

    RecentItem toRecentItem( JsonNode item )
    {
        String id = ArrClient.text( item, "Id" );
        return new RecentItem( id, kindOf( item ), titleOf( item ), detailOf( item ), jellyfin.imageUrl( imageIdOf( item ) ),
                jellyfin.itemUrl( id ), instant( ArrClient.text( item, "DateCreated" ) ) );
    }

    NowPlayingItem toNowPlaying( JsonNode session )
    {
        JsonNode item = session.path( "NowPlayingItem" );
        JsonNode state = session.path( "PlayState" );
        long runtime = item.path( "RunTimeTicks" ).asLong( 0 );
        long position = state.path( "PositionTicks" ).asLong( 0 );
        double progress = runtime > 0 ? Math.round( (double) position / runtime * 1000 ) / 10.0 : 0;
        String device = join( ArrClient.text( session, "Client" ), ArrClient.text( session, "DeviceName" ) );
        return new NowPlayingItem( ArrClient.text( session, "UserName" ), titleOf( item ), detailOf( item ), device,
                Math.max( 0, Math.min( 100, progress ) ), state.path( "IsPaused" ).asBoolean( false ),
                jellyfin.imageUrl( imageIdOf( item ) ) );
    }

    static SearchResult toSearchResult( JsonNode item, boolean movie )
    {
        long id = item.path( movie ? "tmdbId" : "tvdbId" ).asLong( 0 );
        int year = item.path( "year" ).asInt( 0 );
        String poster = null;
        for ( JsonNode image : item.path( "images" ) )
        {
            if ( "poster".equalsIgnoreCase( ArrClient.text( image, "coverType" ) ) )
            {
                poster = firstNonNull( ArrClient.text( image, "remoteUrl" ), ArrClient.text( image, "url" ) );
                break;
            }
        }
        String overview = ArrClient.text( item, "overview" );
        if ( overview != null && overview.length() > OVERVIEW_TEXT_LIMIT )
        {
            overview = overview.substring( 0, OVERVIEW_TEXT_LIMIT - 1 ).trim() + "…";
        }
        return new SearchResult( movie ? "movie" : "series", id, ArrClient.text( item, "title" ), year > 0 ? year : null,
                overview, poster, item.path( "id" ).asLong( 0 ) > 0 );
    }

    // ---- itens do Jellyfin ----

    private static String kindOf( JsonNode item )
    {
        return switch ( String.valueOf( ArrClient.text( item, "Type" ) ) )
        {
            case "Movie" -> "movie";
            case "Episode", "Season", "Series" -> "episode";
            default -> "other";
        };
    }

    private static String titleOf( JsonNode item )
    {
        String type = String.valueOf( ArrClient.text( item, "Type" ) );
        if ( "Episode".equals( type ) || "Season".equals( type ) )
        {
            return firstNonNull( ArrClient.text( item, "SeriesName" ), ArrClient.text( item, "Name" ) );
        }
        return ArrClient.text( item, "Name" );
    }

    private static String detailOf( JsonNode item )
    {
        String type = String.valueOf( ArrClient.text( item, "Type" ) );
        return switch ( type )
        {
            case "Episode" -> join( episodeCode( item.path( "ParentIndexNumber" ).asInt( 0 ), item.path( "IndexNumber" ).asInt( 0 ) ),
                    ArrClient.text( item, "Name" ) );
            case "Season" -> ArrClient.text( item, "Name" );
            case "Series" -> {
                int count = item.path( "ChildCount" ).asInt( 0 );
                yield count > 1 ? count + " episódios novos" : "Episódio novo";
            }
            default -> {
                int year = item.path( "ProductionYear" ).asInt( 0 );
                yield year > 0 ? String.valueOf( year ) : null;
            }
        };
    }

    /** Episodios e temporadas usam a capa da serie. */
    private static String imageIdOf( JsonNode item )
    {
        String type = String.valueOf( ArrClient.text( item, "Type" ) );
        String series = ArrClient.text( item, "SeriesId" );
        if ( ( "Episode".equals( type ) || "Season".equals( type ) ) && series != null )
        {
            return series;
        }
        return ArrClient.text( item, "Id" );
    }

    // ---- utilitarios ----

    private static <T> T attempt( Supplier<T> call, List<String> errors )
    {
        try
        {
            return call.get();
        }
        catch ( MediaException e )
        {
            errors.add( e.getMessage() );
            return null;
        }
    }

    private static Part join( Future<Part> future )
    {
        try
        {
            return future.get();
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new MediaException( "Consulta interrompida", e );
        }
        catch ( ExecutionException e )
        {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new MediaException( "Falha ao consultar", e );
        }
    }

    static String episodeCode( int season, int episode )
    {
        return String.format( Locale.ROOT, "S%02dE%02d", season, episode );
    }

    private static String join( String first, String second )
    {
        if ( first == null || first.isBlank() )
        {
            return second;
        }
        if ( second == null || second.isBlank() )
        {
            return first;
        }
        return first + " · " + second;
    }

    private static String firstNonNull( String first, String second )
    {
        return first != null ? first : second;
    }

    private static String lower( String value )
    {
        return value == null ? "" : value.toLowerCase( Locale.ROOT );
    }

    private static Instant instant( String value )
    {
        if ( value == null || value.isBlank() )
        {
            return null;
        }
        try
        {
            return Instant.parse( value );
        }
        catch ( DateTimeParseException e )
        {
            return null;
        }
    }
}

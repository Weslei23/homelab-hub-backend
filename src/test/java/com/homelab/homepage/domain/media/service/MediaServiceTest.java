package com.homelab.homepage.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.domain.media.client.ArrClient;
import com.homelab.homepage.domain.media.client.ArrClients;
import com.homelab.homepage.domain.media.client.JellyfinClient;
import com.homelab.homepage.domain.media.client.MediaException;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaOverview;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequest;
import com.homelab.homepage.domain.media.dto.MediaDtos.QueueItem;
import com.homelab.homepage.domain.media.dto.MediaDtos.SearchResult;
import com.homelab.homepage.domain.media.dto.MediaDtos.ServiceInfo;
import com.homelab.homepage.domain.media.dto.MediaDtos.UpcomingItem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MediaServiceTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** 22/11/2026, meio-dia UTC. */
    private static final Clock CLOCK = Clock.fixed( Instant.parse( "2026-11-22T12:00:00Z" ), ZoneOffset.UTC );
    private static final LocalDate TODAY = LocalDate.of( 2026, 11, 22 );

    private ArrClient sonarr;
    private ArrClient radarr;
    private JellyfinClient jellyfin;
    private MediaService service;

    private static JsonNode json( String raw )
    {
        return JSON.readTree( raw );
    }

    private static List<JsonNode> list( String raw )
    {
        JsonNode array = json( raw );
        return IntStream.range( 0, array.size() ).mapToObj( array::get ).toList();
    }

    @BeforeEach
    void setUp()
    {
        sonarr = mock( ArrClient.class );
        radarr = mock( ArrClient.class );
        jellyfin = mock( JellyfinClient.class );
        when( sonarr.kind() ).thenReturn( ArrClient.Kind.SONARR );
        when( radarr.kind() ).thenReturn( ArrClient.Kind.RADARR );
        when( sonarr.baseUrl() ).thenReturn( "http://nas:8989" );
        when( radarr.baseUrl() ).thenReturn( "http://nas:7878" );
        when( jellyfin.baseUrl() ).thenReturn( "http://nas:8097" );
        when( jellyfin.imageUrl( any() ) ).thenAnswer( i -> i.getArgument( 0 ) == null ? null : "img/" + i.getArgument( 0 ) );
        when( jellyfin.itemUrl( any() ) ).thenAnswer( i -> "item/" + i.getArgument( 0 ) );
        service = new MediaService( new ArrClients( sonarr, radarr ), jellyfin, "HD-1080p", CLOCK );
    }

    private void allOnline()
    {
        for ( ArrClient client : List.of( sonarr, radarr ) )
        {
            when( client.isConfigured() ).thenReturn( true );
            when( client.version() ).thenReturn( "5.0" );
            when( client.queue() ).thenReturn( List.of() );
            when( client.calendar( any(), any() ) ).thenReturn( List.of() );
        }
        when( jellyfin.isConfigured() ).thenReturn( true );
        when( jellyfin.version() ).thenReturn( "10.11.10" );
        when( jellyfin.latest( anyInt() ) ).thenReturn( List.of() );
        when( jellyfin.sessions() ).thenReturn( List.of() );
    }

    @Nested
    class Painel
    {
        @Test
        void nadaConfigurado()
        {
            MediaOverview overview = service.overview();

            assertThat( overview.services().sonarr() ).isEqualTo( ServiceInfo.notConfigured() );
            assertThat( overview.services().radarr().configured() ).isFalse();
            assertThat( overview.services().jellyfin().configured() ).isFalse();
            assertThat( overview.queue() ).isEmpty();
            assertThat( overview.errors() ).isEmpty();
            verify( sonarr, never() ).version();
        }

        @Test
        void tudoOnline()
        {
            allOnline();

            MediaOverview overview = service.overview();

            assertThat( overview.services().sonarr() ).isEqualTo( new ServiceInfo( true, true, "5.0", "http://nas:8989" ) );
            assertThat( overview.services().jellyfin().version() ).isEqualTo( "10.11.10" );
            assertThat( overview.errors() ).isEmpty();
            verify( sonarr ).calendar( TODAY, TODAY.plusDays( MediaService.UPCOMING_DAYS ) );
        }

        @Test
        void servicoForaDoArNaoEscondeOsOutros()
        {
            allOnline();
            when( sonarr.version() ).thenThrow( new MediaException( "Sonarr indisponivel em http://nas:8989" ) );
            when( radarr.queue() ).thenReturn( list( "[{\"movie\":{\"title\":\"Duna\",\"year\":2021},\"size\":100,\"sizeleft\":25}]" ) );

            MediaOverview overview = service.overview();

            assertThat( overview.services().sonarr().reachable() ).isFalse();
            assertThat( overview.services().sonarr().url() ).isEqualTo( "http://nas:8989" );
            assertThat( overview.errors() ).containsExactly( "Sonarr indisponivel em http://nas:8989" );
            assertThat( overview.queue() ).extracting( QueueItem::title ).containsExactly( "Duna" );
            verify( sonarr, never() ).queue();
        }

        @Test
        void falhaNumaSecaoMantemAsOutras()
        {
            allOnline();
            when( jellyfin.sessions() ).thenThrow( new MediaException( "O Jellyfin respondeu HTTP 500" ) );
            when( jellyfin.latest( anyInt() ) ).thenReturn( list( "[{\"Id\":\"1\",\"Type\":\"Movie\",\"Name\":\"Duna\"}]" ) );

            MediaOverview overview = service.overview();

            assertThat( overview.services().jellyfin().reachable() ).isTrue();
            assertThat( overview.recent() ).hasSize( 1 );
            assertThat( overview.nowPlaying() ).isEmpty();
            assertThat( overview.errors() ).containsExactly( "O Jellyfin respondeu HTTP 500" );
        }

        @Test
        void filaJuntaSeriesEFilmes()
        {
            allOnline();
            when( sonarr.queue() ).thenReturn( list( "[{\"series\":{\"title\":\"Reacher\"},\"episode\":{\"seasonNumber\":3,"
                    + "\"episodeNumber\":5,\"title\":\"Unfinished Business\"},\"size\":200,\"sizeleft\":50,\"status\":\"downloading\","
                    + "\"timeleft\":\"00:05:00\"}]" ) );
            when( radarr.queue() ).thenReturn( list( "[{\"movie\":{\"title\":\"Duna\",\"year\":2021},\"status\":\"queued\"}]" ) );

            List<QueueItem> queue = service.overview().queue();

            assertThat( queue ).containsExactly(
                    new QueueItem( "episode", "Reacher", "S03E05 · Unfinished Business", "downloading", 75.0, "00:05:00", null ),
                    new QueueItem( "movie", "Duna", "2021", "queued", 0, null, null ) );
        }

        @Test
        void proximosOrdenadosELimitados()
        {
            allOnline();
            StringBuilder episodes = new StringBuilder( "[" );
            for ( int i = 25; i >= 1; i-- )
            {
                episodes.append( i < 25 ? "," : "" )
                        .append( "{\"series\":{\"title\":\"S\"},\"seasonNumber\":1,\"episodeNumber\":" ).append( i )
                        .append( ",\"airDateUtc\":\"2026-11-23T10:" ).append( String.format( "%02d", i ) ).append( ":00Z\"}" );
            }
            when( sonarr.calendar( any(), any() ) ).thenReturn( list( episodes.append( "]" ).toString() ) );
            when( radarr.calendar( any(), any() ) ).thenReturn( list(
                    "[{\"title\":\"Duna 3\",\"year\":2026,\"digitalRelease\":\"2026-11-23T00:00:00Z\"}]" ) );

            List<UpcomingItem> upcoming = service.overview().upcoming();

            assertThat( upcoming ).hasSize( MediaService.UPCOMING_LIMIT );
            assertThat( upcoming ).extracting( UpcomingItem::date ).isSorted();
            assertThat( upcoming.get( 0 ) ).isEqualTo( new UpcomingItem( "movie", "Duna 3 (2026)", "Lançamento digital",
                    Instant.parse( "2026-11-23T00:00:00Z" ), false ) );
            assertThat( upcoming.get( 1 ).detail() ).isEqualTo( "S01E01" );
            assertThat( upcoming.getLast().detail() ).isEqualTo( "S01E19" );
        }
    }

    @Nested
    class Conversoes
    {
        @ParameterizedTest
        @CsvSource( {
                "downloading, downloading, ok, downloading",
                "queued, downloading, ok, queued",
                "delay, downloading, ok, queued",
                "paused, downloading, ok, paused",
                "completed, importPending, ok, importing",
                "completed, downloading, ok, importing",
                "downloading, downloading, warning, warning",
                "failed, downloading, ok, failed",
                "downloading, failedPending, ok, failed",
                "'', '', '', downloading" } )
        void estadoDaFila( String status, String tracked, String health, String expected )
        {
            JsonNode record = json( "{\"status\":\"" + status + "\",\"trackedDownloadState\":\"" + tracked
                    + "\",\"trackedDownloadStatus\":\"" + health + "\"}" );

            assertThat( MediaService.queueState( record ) ).isEqualTo( expected );
        }

        @Test
        void avisoDaFilaVemDoErroOuDasMensagens()
        {
            assertThat( MediaService.toQueueItem( json( "{\"errorMessage\":\"Sem espaco\"}" ), false ).message() )
                    .isEqualTo( "Sem espaco" );
            assertThat( MediaService.toQueueItem( json( "{\"statusMessages\":[{\"title\":\"x.mkv\",\"messages\":[\"Amostra\"]}]}" ),
                    false ).message() ).isEqualTo( "Amostra" );
            assertThat( MediaService.toQueueItem( json( "{\"statusMessages\":[{\"title\":\"x.mkv\",\"messages\":[]}]}" ), false )
                    .message() ).isEqualTo( "x.mkv" );
        }

        @Test
        void filaSemSerieOuFilmeUsaONomeDoLancamento()
        {
            QueueItem item = MediaService.toQueueItem( json( "{\"title\":\"Release.1080p\",\"size\":0}" ), true );

            assertThat( item.title() ).isEqualTo( "Release.1080p" );
            assertThat( item.detail() ).isNull();
            assertThat( item.progress() ).isZero();
        }

        @Test
        void filmeForaDaJanelaNaoAparece()
        {
            Instant from = Instant.parse( "2026-11-22T00:00:00Z" );
            Instant to = Instant.parse( "2026-12-07T00:00:00Z" );

            assertThat( MediaService.toUpcomingMovie( json( "{\"title\":\"A\",\"inCinemas\":\"2026-10-01T00:00:00Z\"}" ), from, to ) )
                    .isNull();
            UpcomingItem item = MediaService.toUpcomingMovie( json( "{\"title\":\"A\",\"inCinemas\":\"2026-11-30T00:00:00Z\","
                    + "\"physicalRelease\":\"2026-11-25T00:00:00Z\",\"digitalRelease\":\"2027-01-01T00:00:00Z\",\"hasFile\":true}" ),
                    from, to );
            assertThat( item ).isEqualTo( new UpcomingItem( "movie", "A", "Lançamento em mídia física",
                    Instant.parse( "2026-11-25T00:00:00Z" ), true ) );
        }

        @Test
        void episodioSemDataNaoAparece()
        {
            assertThat( MediaService.toUpcomingEpisode( json( "{\"title\":\"x\"}" ) ) ).isNull();
            assertThat( MediaService.toUpcomingEpisode( json( "{\"airDateUtc\":\"lixo\"}" ) ) ).isNull();
        }

        @Test
        void recentesDoJellyfin()
        {
            var movie = service.toRecentItem( json( "{\"Id\":\"m1\",\"Type\":\"Movie\",\"Name\":\"Duna\",\"ProductionYear\":2021,"
                    + "\"DateCreated\":\"2026-11-20T10:00:00.1234567Z\"}" ) );
            var episode = service.toRecentItem( json( "{\"Id\":\"e1\",\"Type\":\"Episode\",\"Name\":\"Piloto\",\"SeriesName\":\"Lupin\","
                    + "\"SeriesId\":\"s1\",\"ParentIndexNumber\":1,\"IndexNumber\":1}" ) );
            var series = service.toRecentItem( json( "{\"Id\":\"s2\",\"Type\":\"Series\",\"Name\":\"Reacher\",\"ChildCount\":3}" ) );

            assertThat( movie.kind() ).isEqualTo( "movie" );
            assertThat( movie.detail() ).isEqualTo( "2021" );
            assertThat( movie.imageUrl() ).isEqualTo( "img/m1" );
            assertThat( movie.link() ).isEqualTo( "item/m1" );
            assertThat( movie.addedAt() ).isEqualTo( Instant.parse( "2026-11-20T10:00:00.1234567Z" ) );
            assertThat( episode.title() ).isEqualTo( "Lupin" );
            assertThat( episode.detail() ).isEqualTo( "S01E01 · Piloto" );
            assertThat( episode.imageUrl() ).isEqualTo( "img/s1" );
            assertThat( series.detail() ).isEqualTo( "3 episódios novos" );
        }

        @Test
        void assistindoAgora()
        {
            var playing = service.toNowPlaying( json( "{\"UserName\":\"ana\",\"Client\":\"Jellyfin Web\",\"DeviceName\":\"Chrome\","
                    + "\"NowPlayingItem\":{\"Id\":\"m1\",\"Type\":\"Movie\",\"Name\":\"Duna\",\"RunTimeTicks\":1000},"
                    + "\"PlayState\":{\"PositionTicks\":250,\"IsPaused\":true}}" ) );

            assertThat( playing.user() ).isEqualTo( "ana" );
            assertThat( playing.title() ).isEqualTo( "Duna" );
            assertThat( playing.device() ).isEqualTo( "Jellyfin Web · Chrome" );
            assertThat( playing.progress() ).isEqualTo( 25.0 );
            assertThat( playing.paused() ).isTrue();
        }

        @Test
        void sessoesSemNadaTocandoFicamDeFora()
        {
            allOnline();
            when( jellyfin.sessions() ).thenReturn( list( "[{\"UserName\":\"ana\"},{\"UserName\":\"bia\",\"NowPlayingItem\":"
                    + "{\"Type\":\"Movie\",\"Name\":\"X\"}}]" ) );

            assertThat( service.overview().nowPlaying() ).extracting( p -> p.user() ).containsExactly( "bia" );
        }
    }

    @Nested
    class Busca
    {
        @Test
        void termoCurto()
        {
            assertThatThrownBy( () -> service.search( "movie", " a " ) ).isInstanceOf( BadRequestException.class );
        }

        @Test
        void tipoInvalido()
        {
            assertThatThrownBy( () -> service.search( "musica", "abc" ) ).hasMessage( "O tipo deve ser movie ou series" );
        }

        @Test
        void servicoNaoConfigurado()
        {
            assertThatThrownBy( () -> service.search( "series", "Lupin" ) ).hasMessage( "Sonarr nao configurado no backend" );
        }

        @Test
        void filmes()
        {
            when( radarr.isConfigured() ).thenReturn( true );
            String longText = "x".repeat( 400 );
            when( radarr.lookup( "Duna" ) ).thenReturn( list( "[{\"title\":\"Duna\",\"tmdbId\":438631,\"year\":2021,\"id\":4,"
                    + "\"overview\":\"" + longText + "\",\"images\":[{\"coverType\":\"fanart\",\"remoteUrl\":\"f\"},"
                    + "{\"coverType\":\"poster\",\"remoteUrl\":\"p\"}]},{\"title\":\"Sem id\",\"tmdbId\":0}]" ) );

            List<SearchResult> results = service.search( "movie", " Duna " );

            assertThat( results ).hasSize( 1 );
            SearchResult duna = results.getFirst();
            assertThat( duna.id() ).isEqualTo( 438631 );
            assertThat( duna.posterUrl() ).isEqualTo( "p" );
            assertThat( duna.inLibrary() ).isTrue();
            assertThat( duna.overview() ).hasSize( 300 ).endsWith( "…" );
        }

        @Test
        void seriesUsamTvdbELimitam()
        {
            when( sonarr.isConfigured() ).thenReturn( true );
            StringBuilder many = new StringBuilder( "[" );
            for ( int i = 1; i <= 20; i++ )
            {
                many.append( i > 1 ? "," : "" ).append( "{\"title\":\"S" ).append( i ).append( "\",\"tvdbId\":" ).append( i ).append( "}" );
            }
            when( sonarr.lookup( "S" + "x" ) ).thenReturn( list( many.append( "]" ).toString() ) );

            List<SearchResult> results = service.search( "series", "Sx" );

            assertThat( results ).hasSize( MediaService.SEARCH_LIMIT );
            assertThat( results.getFirst() ).isEqualTo( new SearchResult( "series", 1, "S1", null, null, null, false ) );
        }
    }

    @Nested
    class Pedido
    {
        @BeforeEach
        void arrPronto()
        {
            for ( ArrClient client : List.of( sonarr, radarr ) )
            {
                when( client.isConfigured() ).thenReturn( true );
                when( client.qualityProfiles() ).thenReturn( list( "[{\"id\":1,\"name\":\"Any\"},{\"id\":4,\"name\":\"hd-1080p\"}]" ) );
                when( client.rootFolders() ).thenReturn( list( "[{\"path\":\"/data/Media\"}]" ) );
            }
        }

        private JsonNode added( ArrClient client )
        {
            ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass( JsonNode.class );
            verify( client ).add( captor.capture() );
            return captor.getValue();
        }

        @Test
        void filmeComecaABuscar()
        {
            when( radarr.lookupById( 438631 ) ).thenReturn( Optional.of( json( "{\"title\":\"Duna\",\"tmdbId\":438631}" ) ) );

            var response = service.request( new MediaRequest( "movie", 438631L, null ) );

            JsonNode body = added( radarr );
            assertThat( body.get( "qualityProfileId" ).asLong() ).isEqualTo( 4 );
            assertThat( body.get( "rootFolderPath" ).asString() ).isEqualTo( "/data/Media" );
            assertThat( body.get( "monitored" ).asBoolean() ).isTrue();
            assertThat( body.get( "minimumAvailability" ).asString() ).isEqualTo( "released" );
            assertThat( body.get( "addOptions" ).get( "searchForMovie" ).asBoolean() ).isTrue();
            assertThat( body.get( "tmdbId" ).asLong() ).isEqualTo( 438631 );
            assertThat( response.title() ).isEqualTo( "Duna" );
            assertThat( response.message() ).isEqualTo( "Radarr adicionou \"Duna\" e ja esta procurando" );
        }

        @Test
        void serieUsaAOpcaoDeEpisodios()
        {
            when( sonarr.lookupById( 366924 ) ).thenReturn( Optional.of( json( "{\"title\":\"Reacher\",\"tvdbId\":366924}" ) ) );

            service.request( new MediaRequest( "series", 366924L, "future" ) );

            JsonNode body = added( sonarr );
            assertThat( body.get( "seasonFolder" ).asBoolean() ).isTrue();
            assertThat( body.get( "addOptions" ).get( "monitor" ).asString() ).isEqualTo( "future" );
            assertThat( body.get( "addOptions" ).get( "searchForMissingEpisodes" ).asBoolean() ).isTrue();
        }

        @Test
        void serieSemOpcaoMonitoraTudo()
        {
            when( sonarr.lookupById( 1 ) ).thenReturn( Optional.of( json( "{\"title\":\"X\"}" ) ) );

            service.request( new MediaRequest( "series", 1L, null ) );

            assertThat( added( sonarr ).get( "addOptions" ).get( "monitor" ).asString() ).isEqualTo( "all" );
        }

        @Test
        void perfilInexistenteUsaOPrimeiro()
        {
            service = new MediaService( new ArrClients( sonarr, radarr ), jellyfin, "Ultra-HD", CLOCK );
            when( radarr.lookupById( 1 ) ).thenReturn( Optional.of( json( "{\"title\":\"X\"}" ) ) );

            service.request( new MediaRequest( "movie", 1L, null ) );

            assertThat( added( radarr ).get( "qualityProfileId" ).asLong() ).isEqualTo( 1 );
        }

        @Test
        void jaNaBiblioteca()
        {
            when( radarr.lookupById( 1 ) ).thenReturn( Optional.of( json( "{\"title\":\"Duna\",\"id\":9}" ) ) );

            assertThatThrownBy( () -> service.request( new MediaRequest( "movie", 1L, null ) ) )
                    .isInstanceOf( ConflictException.class )
                    .hasMessage( "\"Duna\" ja esta na biblioteca do Radarr" );
            verify( radarr, never() ).add( any() );
        }

        @Test
        void tituloNaoEncontrado()
        {
            when( sonarr.lookupById( 1 ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> service.request( new MediaRequest( "series", 1L, null ) ) )
                    .hasMessage( "Titulo nao encontrado no Sonarr" );
        }

        @Test
        void semPastaRaiz()
        {
            when( radarr.lookupById( 1 ) ).thenReturn( Optional.of( json( "{\"title\":\"X\"}" ) ) );
            when( radarr.rootFolders() ).thenReturn( List.of() );

            assertThatThrownBy( () -> service.request( new MediaRequest( "movie", 1L, null ) ) )
                    .hasMessageStartingWith( "Cadastre uma pasta raiz no Radarr" );
        }

        @Test
        void semPerfil()
        {
            when( radarr.lookupById( 1 ) ).thenReturn( Optional.of( json( "{\"title\":\"X\"}" ) ) );
            when( radarr.qualityProfiles() ).thenReturn( List.of() );

            assertThatThrownBy( () -> service.request( new MediaRequest( "movie", 1L, null ) ) )
                    .hasMessage( "Nenhum perfil de qualidade no Radarr" );
        }
    }
}

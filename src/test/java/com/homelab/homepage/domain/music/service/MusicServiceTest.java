package com.homelab.homepage.domain.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.music.client.DowntifyClient;
import com.homelab.homepage.domain.music.client.DowntifyException;
import com.homelab.homepage.domain.music.dto.MusicDtos.DownloadResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.MusicStatusResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistRequest;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.QueueItemResponse;
import com.homelab.homepage.domain.music.model.MusicPlaylist;
import com.homelab.homepage.domain.music.repository.MusicPlaylistRepository;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MusicServiceTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ID = "37i9dQZF1DXcBWIGoYBM5M";
    private static final String PLAYLIST = "https://open.spotify.com/playlist/" + ID;
    private static final String TRACK = "https://open.spotify.com/track/" + ID;
    private static final String ALBUM = "https://open.spotify.com/album/" + ID;

    private DowntifyClient downtify;
    private MusicPlaylistRepository repository;
    private DiscordNotifier notifier;
    private MusicService service;

    @BeforeEach
    void setUp()
    {
        downtify = mock( DowntifyClient.class );
        repository = mock( MusicPlaylistRepository.class );
        notifier = mock( DiscordNotifier.class );
        when( downtify.isConfigured() ).thenReturn( true );
        when( repository.save( any( MusicPlaylist.class ) ) ).thenAnswer( inv -> {
            MusicPlaylist playlist = inv.getArgument( 0 );
            if ( playlist.getId() == null )
            {
                ReflectionTestUtils.setField( playlist, "id", 1L );
            }
            return playlist;
        } );
        service = new MusicService( downtify, repository, notifier, "6h" );
    }

    private static JsonNode song( String id, String name, String... artists )
    {
        StringBuilder json = new StringBuilder( "{\"song_id\":\"" + id + "\",\"name\":\"" + name + "\",\"list_name\":\"Daft Punk Mix\",\"artists\":[" );
        for ( int i = 0; i < artists.length; i++ )
        {
            json.append( i > 0 ? "," : "" ).append( '"' ).append( artists[i] ).append( '"' );
        }
        return JSON.readTree( json.append( "]}" ).toString() );
    }

    private static MusicPlaylist playlist( Long id, String name, String... knownSongs )
    {
        MusicPlaylist playlist = new MusicPlaylist( name, PLAYLIST );
        ReflectionTestUtils.setField( playlist, "id", id );
        playlist.getKnownSongs().addAll( List.of( knownSongs ) );
        return playlist;
    }

    @SuppressWarnings( "unchecked" )
    private List<JsonNode> sentToDowntify()
    {
        ArgumentCaptor<List<JsonNode>> captor = ArgumentCaptor.forClass( List.class );
        verify( downtify ).downloadBatch( captor.capture(), any() );
        return captor.getValue();
    }

    @Nested
    class Status
    {
        @Test
        void naoConfigurado()
        {
            when( downtify.isConfigured() ).thenReturn( false );

            assertThat( service.status() ).isEqualTo( new MusicStatusResponse( false, false, null, "6h" ) );
            verify( downtify, never() ).version();
        }

        @Test
        void online()
        {
            when( downtify.version() ).thenReturn( Optional.of( "3.12.0" ) );

            assertThat( service.status() ).isEqualTo( new MusicStatusResponse( true, true, "3.12.0", "6h" ) );
        }

        @Test
        void configuradoMasForaDoAr()
        {
            when( downtify.version() ).thenReturn( Optional.empty() );

            assertThat( service.status() ).isEqualTo( new MusicStatusResponse( true, false, null, "6h" ) );
        }
    }

    @Nested
    class DownloadImediato
    {
        @Test
        void faixa()
        {
            when( downtify.resolve( TRACK ) ).thenReturn( List.of( song( "1", "One More Time", "Daft Punk" ) ) );

            DownloadResponse response = service.download( TRACK + "?si=abc" );

            assertThat( response ).isEqualTo( new DownloadResponse( 1, "faixa" ) );
            verify( downtify ).downloadBatch( anyList(), isNull() );
        }

        @Test
        void albumNaoGeraPlaylist()
        {
            when( downtify.resolve( ALBUM ) ).thenReturn( List.of( song( "1", "A" ), song( "2", "B" ) ) );

            assertThat( service.download( ALBUM ) ).isEqualTo( new DownloadResponse( 2, "album" ) );
            verify( downtify ).downloadBatch( anyList(), isNull() );
        }

        @Test
        void playlistEnviaOLinkParaGerarM3u()
        {
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ) ) );

            service.download( "https://open.spotify.com/intl-pt/playlist/" + ID );

            verify( downtify ).downloadBatch( anyList(), eq( PLAYLIST ) );
        }

        @Test
        void faixasRepetidasSaoEnviadasUmaVez()
        {
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ), song( "2", "B" ), song( "1", "A" ) ) );

            assertThat( service.download( PLAYLIST ).queued() ).isEqualTo( 2 );
            assertThat( sentToDowntify() ).hasSize( 2 );
        }

        @Test
        void linkInvalidoNemChamaODowntify()
        {
            assertThatThrownBy( () -> service.download( "https://youtube.com/watch?v=x" ) )
                    .isInstanceOf( BadRequestException.class );
            verify( downtify, never() ).resolve( anyString() );
        }

        @Test
        void linkSemFaixas()
        {
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of() );

            assertThatThrownBy( () -> service.download( PLAYLIST ) )
                    .isInstanceOf( BadRequestException.class )
                    .hasMessage( "Nenhuma faixa encontrada nesse link" );
            verify( downtify, never() ).downloadBatch( anyList(), any() );
        }

        @Test
        void erroDoDowntifyEPropagado()
        {
            when( downtify.resolve( TRACK ) ).thenThrow( new DowntifyException( "Downtify indisponivel em http://x" ) );

            assertThatThrownBy( () -> service.download( TRACK ) ).isInstanceOf( DowntifyException.class );
        }
    }

    @Nested
    class Fila
    {
        @Test
        void converteOsItensDaFila()
        {
            when( downtify.queue() ).thenReturn( List.of(
                    JSON.readTree( "{\"song\":{\"name\":\"One More Time\",\"artists\":[\"Daft Punk\"]},\"status\":\"downloading\",\"progress\":42.5,\"message\":\"Baixando\"}" ),
                    JSON.readTree( "{\"song\":{\"name\":\"Aerodynamic\"},\"status\":\"queued\",\"progress\":null}" ) ) );

            List<QueueItemResponse> queue = service.queue();

            assertThat( queue ).containsExactly(
                    new QueueItemResponse( "One More Time", "Daft Punk", "downloading", 42.5, "Baixando" ),
                    new QueueItemResponse( "Aerodynamic", null, "queued", null, null ) );
        }

        @Test
        void itemSemSongNaoQuebra()
        {
            when( downtify.queue() ).thenReturn( List.of( JSON.readTree( "{\"status\":\"error\"}" ) ) );

            assertThat( service.queue() ).containsExactly( new QueueItemResponse( null, null, "error", null, null ) );
        }

        @Test
        void limparConcluidos()
        {
            service.clearCompletedQueue();

            verify( downtify ).clearCompleted();
        }
    }

    @Nested
    class Playlists
    {
        @Test
        void cadastrarFazAPrimeiraSincronizacaoEUsaONomeDoSpotify()
        {
            when( repository.existsByUrl( PLAYLIST ) ).thenReturn( false );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ), song( "2", "B" ) ) );

            PlaylistResponse response = service.addPlaylist( new PlaylistRequest( PLAYLIST + "?si=x", null ) );

            assertThat( response.name() ).isEqualTo( "Daft Punk Mix" );
            assertThat( response.url() ).isEqualTo( PLAYLIST );
            assertThat( response.enabled() ).isTrue();
            assertThat( response.knownTracks() ).isEqualTo( 2 );
            assertThat( response.lastSyncOk() ).isTrue();
            assertThat( response.lastSyncMessage() ).isEqualTo( "2 faixa(s) nova(s) enviada(s) ao Downtify" );
            assertThat( sentToDowntify() ).hasSize( 2 );
        }

        @Test
        void nomeInformadoTemPrioridade()
        {
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ) ) );

            assertThat( service.addPlaylist( new PlaylistRequest( PLAYLIST, "  Academia  " ) ).name() ).isEqualTo( "Academia" );
        }

        @Test
        void semNomeNoSpotifyUsaOId()
        {
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( JSON.readTree( "{\"song_id\":\"1\",\"name\":\"A\"}" ) ) );

            assertThat( service.addPlaylist( new PlaylistRequest( PLAYLIST, "" ) ).name() ).isEqualTo( "Playlist " + ID );
        }

        @Test
        void soPlaylistsPodemSerAcompanhadas()
        {
            assertThatThrownBy( () -> service.addPlaylist( new PlaylistRequest( ALBUM, null ) ) )
                    .isInstanceOf( BadRequestException.class )
                    .hasMessageContaining( "So playlists" );
            verify( repository, never() ).save( any() );
        }

        @Test
        void playlistRepetidaEConflito()
        {
            when( repository.existsByUrl( PLAYLIST ) ).thenReturn( true );

            assertThatThrownBy( () -> service.addPlaylist( new PlaylistRequest( "spotify:playlist:" + ID, null ) ) )
                    .isInstanceOf( ConflictException.class )
                    .hasMessage( "Essa playlist ja esta cadastrada" );
        }

        @Test
        void downtifyForaDoArNoCadastroGuardaAPlaylistComOErro()
        {
            when( downtify.resolve( PLAYLIST ) ).thenThrow( new DowntifyException( "Downtify indisponivel em http://x" ) );

            PlaylistResponse response = service.addPlaylist( new PlaylistRequest( PLAYLIST, null ) );

            assertThat( response.lastSyncOk() ).isFalse();
            assertThat( response.lastSyncMessage() ).isEqualTo( "Downtify indisponivel em http://x" );
            assertThat( response.knownTracks() ).isZero();
        }

        @Test
        void listar()
        {
            when( repository.findAllByOrderByNameAsc() ).thenReturn( List.of( playlist( 1L, "A", "x", "y" ) ) );

            assertThat( service.listPlaylists() ).singleElement()
                    .satisfies( p -> assertThat( p.knownTracks() ).isEqualTo( 2 ) );
        }

        @Test
        void desligarAutomatico()
        {
            MusicPlaylist playlist = playlist( 1L, "A" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );

            assertThat( service.setEnabled( 1L, false ).enabled() ).isFalse();
            assertThat( playlist.isEnabled() ).isFalse();
        }

        @Test
        void excluir()
        {
            MusicPlaylist playlist = playlist( 1L, "A" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );

            service.deletePlaylist( 1L );

            verify( repository ).delete( playlist );
        }

        @Test
        void playlistInexistente()
        {
            when( repository.findById( 9L ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> service.syncNow( 9L ) ).isInstanceOf( ResourceNotFoundException.class )
                    .hasMessage( "Playlist nao encontrada" );
            assertThatThrownBy( () -> service.setEnabled( 9L, true ) ).isInstanceOf( ResourceNotFoundException.class );
            assertThatThrownBy( () -> service.deletePlaylist( 9L ) ).isInstanceOf( ResourceNotFoundException.class );
        }
    }

    @Nested
    class Sincronizacao
    {
        @Test
        void soFaixasNovasVaoParaOBatch()
        {
            MusicPlaylist playlist = playlist( 1L, "Mix", "1", "2" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ), song( "2", "B" ), song( "3", "C", "Daft Punk" ) ) );

            PlaylistResponse response = service.syncNow( 1L );

            assertThat( sentToDowntify() ).extracting( s -> s.get( "song_id" ).asString() ).containsExactly( "3" );
            verify( downtify ).downloadBatch( anyList(), eq( PLAYLIST ) );
            assertThat( playlist.getKnownSongs() ).containsExactlyInAnyOrder( "1", "2", "3" );
            assertThat( response.lastSyncMessage() ).isEqualTo( "1 faixa(s) nova(s) enviada(s) ao Downtify" );
        }

        @Test
        void avisaNoDiscordComAsFaixasNovas()
        {
            MusicPlaylist playlist = playlist( 1L, "Mix", "1" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ), song( "2", "Get Lucky", "Daft Punk", "Pharrell" ) ) );

            service.syncNow( 1L );

            verify( notifier ).notifyNewMusic( "Mix", PLAYLIST, List.of( "Get Lucky — Daft Punk, Pharrell" ) );
        }

        @Test
        void semFaixasNovasNaoChamaOBatchNemAvisa()
        {
            MusicPlaylist playlist = playlist( 1L, "Mix", "1", "2" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ), song( "2", "B" ) ) );

            PlaylistResponse response = service.syncNow( 1L );

            verify( downtify, never() ).downloadBatch( anyList(), any() );
            verifyNoInteractions( notifier );
            assertThat( response.lastSyncOk() ).isTrue();
            assertThat( response.lastSyncMessage() ).isEqualTo( "Nenhuma faixa nova" );
            assertThat( response.lastSyncAt() ).isNotNull();
        }

        @Test
        void faixaRemovidaDaPlaylistNaoEApagada()
        {
            MusicPlaylist playlist = playlist( 1L, "Mix", "1", "2" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "2", "B" ) ) );

            service.syncNow( 1L );

            assertThat( playlist.getKnownSongs() ).containsExactlyInAnyOrder( "1", "2" );
        }

        @Test
        void sincronizacaoManualNaoAdotaONomeDoSpotify()
        {
            MusicPlaylist playlist = playlist( 1L, "Meu nome", "1" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ) ) );

            assertThat( service.syncNow( 1L ).name() ).isEqualTo( "Meu nome" );
        }

        @Test
        void falhaNoBatchNaoMarcaAsFaixasComoBaixadas()
        {
            MusicPlaylist playlist = playlist( 1L, "Mix" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ) ) );
            doThrow( new DowntifyException( "O Downtify respondeu HTTP 500" ) ).when( downtify ).downloadBatch( anyList(), any() );

            PlaylistResponse response = service.syncNow( 1L );

            assertThat( playlist.getKnownSongs() ).isEmpty();
            assertThat( response.lastSyncOk() ).isFalse();
            assertThat( response.lastSyncMessage() ).isEqualTo( "O Downtify respondeu HTTP 500" );
            verifyNoInteractions( notifier );
        }

        @Test
        void mensagemDeErroLongaECortada()
        {
            MusicPlaylist playlist = playlist( 1L, "Mix" );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( playlist ) );
            when( downtify.resolve( PLAYLIST ) ).thenThrow( new DowntifyException( "x".repeat( 400 ) ) );

            assertThat( service.syncNow( 1L ).lastSyncMessage() ).hasSize( 255 );
        }
    }

    @Nested
    class Agendamento
    {
        @Test
        void sincronizaSoAsPlaylistsLigadas()
        {
            MusicPlaylist a = playlist( 1L, "A" );
            when( repository.findAllByEnabledTrueOrderByNameAsc() ).thenReturn( List.of( a ) );
            when( downtify.resolve( PLAYLIST ) ).thenReturn( List.of( song( "1", "A" ) ) );

            service.syncAll();

            verify( downtify ).downloadBatch( anyList(), eq( PLAYLIST ) );
            assertThat( a.getKnownSongs() ).containsExactly( "1" );
        }

        @Test
        void naoConfiguradoNaoFazNada()
        {
            when( downtify.isConfigured() ).thenReturn( false );

            service.syncAll();

            verifyNoInteractions( repository );
        }

        @Test
        void falhaEmUmaPlaylistNaoImpedeAsOutras()
        {
            MusicPlaylist a = playlist( 1L, "A" );
            MusicPlaylist b = new MusicPlaylist( "B", "https://open.spotify.com/playlist/0000000000000000000000" );
            when( repository.findAllByEnabledTrueOrderByNameAsc() ).thenReturn( List.of( a, b ) );
            when( downtify.resolve( PLAYLIST ) ).thenThrow( new IllegalStateException( "bug inesperado" ) );
            when( downtify.resolve( b.getUrl() ) ).thenReturn( List.of( song( "9", "Z" ) ) );

            service.syncAll();

            assertThat( b.getKnownSongs() ).containsExactly( "9" );
        }

        @Test
        void sincronizacaoManualDuranteAAutomaticaEConflito() throws Exception
        {
            CountDownLatch insideSync = new CountDownLatch( 1 );
            CountDownLatch release = new CountDownLatch( 1 );
            MusicPlaylist a = playlist( 1L, "A" );
            when( repository.findAllByEnabledTrueOrderByNameAsc() ).thenReturn( List.of( a ) );
            when( repository.findById( 1L ) ).thenReturn( Optional.of( a ) );
            doAnswer( inv -> {
                insideSync.countDown();
                release.await( 5, TimeUnit.SECONDS );
                return new ArrayList<JsonNode>();
            } ).when( downtify ).resolve( PLAYLIST );

            Thread scheduled = Thread.ofVirtual().start( service::syncAll );
            assertThat( insideSync.await( 5, TimeUnit.SECONDS ) ).isTrue();

            assertThatThrownBy( () -> service.syncNow( 1L ) )
                    .isInstanceOf( ConflictException.class )
                    .hasMessageContaining( "sincronizacao em andamento" );
            assertThatThrownBy( () -> service.addPlaylist( new PlaylistRequest( "https://open.spotify.com/playlist/1111111111111111111111", null ) ) )
                    .isInstanceOf( ConflictException.class );
            // Nada foi gravado: a playlist nao fica cadastrada pela metade
            verify( repository, never() ).save( any() );

            release.countDown();
            scheduled.join( 5000 );
            // Depois que termina, a manual volta a funcionar
            assertThat( service.syncNow( 1L ).lastSyncOk() ).isTrue();
        }
    }
}

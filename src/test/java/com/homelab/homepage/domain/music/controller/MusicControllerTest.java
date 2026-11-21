package com.homelab.homepage.domain.music.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.GlobalExceptionHandler;
import com.homelab.homepage.domain.music.client.DowntifyException;
import com.homelab.homepage.domain.music.dto.MusicDtos.DownloadResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.MusicStatusResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistRequest;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.QueueItemResponse;
import com.homelab.homepage.domain.music.service.MusicService;

class MusicControllerTest
{
    private static final String PLAYLIST = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";

    private MusicService musicService;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        musicService = mock( MusicService.class );
        mvc = MockMvcBuilders.standaloneSetup( new MusicController( musicService ) )
                .setControllerAdvice( new GlobalExceptionHandler() )
                .build();
    }

    private static MockHttpServletRequestBuilder json( MockHttpServletRequestBuilder builder, String body )
    {
        return builder.contentType( MediaType.APPLICATION_JSON ).content( body );
    }

    private static PlaylistResponse playlist()
    {
        return new PlaylistResponse( 1L, "Mix", PLAYLIST, true, 12, Instant.parse( "2026-11-20T12:00:00Z" ), true, "Nenhuma faixa nova" );
    }

    @Test
    void statusDoDowntify() throws Exception
    {
        when( musicService.status() ).thenReturn( new MusicStatusResponse( true, true, "3.12.0", "6h", "http://192.168.3.182:8582" ) );

        mvc.perform( get( "/api/music/status" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$.configured" ).value( true ) )
                .andExpect( jsonPath( "$.reachable" ).value( true ) )
                .andExpect( jsonPath( "$.version" ).value( "3.12.0" ) )
                .andExpect( jsonPath( "$.syncInterval" ).value( "6h" ) )
                .andExpect( jsonPath( "$.url" ).value( "http://192.168.3.182:8582" ) );
    }

    @Test
    void downloadDevolve202() throws Exception
    {
        when( musicService.download( PLAYLIST ) ).thenReturn( new DownloadResponse( 30, "playlist" ) );

        mvc.perform( json( post( "/api/music/downloads" ), "{\"url\":\"" + PLAYLIST + "\"}" ) )
                .andExpect( status().isAccepted() )
                .andExpect( jsonPath( "$.queued" ).value( 30 ) )
                .andExpect( jsonPath( "$.type" ).value( "playlist" ) );
    }

    @Test
    void downloadSemLinkDevolve400() throws Exception
    {
        mvc.perform( json( post( "/api/music/downloads" ), "{\"url\":\" \"}" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "Cole um link do Spotify" ) );
        verify( musicService, never() ).download( any() );
    }

    @Test
    void linkInvalidoDevolve400() throws Exception
    {
        when( musicService.download( "x" ) ).thenThrow( new BadRequestException( "Use um link do Spotify" ) );

        mvc.perform( json( post( "/api/music/downloads" ), "{\"url\":\"x\"}" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "Use um link do Spotify" ) );
    }

    @Test
    void downtifyForaDoArDevolve502() throws Exception
    {
        when( musicService.download( PLAYLIST ) ).thenThrow( new DowntifyException( "Downtify indisponivel em http://x" ) );

        mvc.perform( json( post( "/api/music/downloads" ), "{\"url\":\"" + PLAYLIST + "\"}" ) )
                .andExpect( status().isBadGateway() )
                .andExpect( jsonPath( "$.message" ).value( "Downtify indisponivel em http://x" ) );
    }

    @Test
    void fila() throws Exception
    {
        when( musicService.queue() ).thenReturn( List.of( new QueueItemResponse( "Get Lucky", "Daft Punk", "downloading", 42.5, null ) ) );

        mvc.perform( get( "/api/music/queue" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$[0].title" ).value( "Get Lucky" ) )
                .andExpect( jsonPath( "$[0].progress" ).value( 42.5 ) );
    }

    @Test
    void listarPlaylists() throws Exception
    {
        when( musicService.listPlaylists() ).thenReturn( List.of( playlist() ) );

        mvc.perform( get( "/api/music/playlists" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$[0].name" ).value( "Mix" ) )
                .andExpect( jsonPath( "$[0].knownTracks" ).value( 12 ) )
                .andExpect( jsonPath( "$[0].lastSyncAt" ).value( "2026-11-20T12:00:00Z" ) );
    }

    @Test
    void cadastrarPlaylist() throws Exception
    {
        when( musicService.addPlaylist( new PlaylistRequest( PLAYLIST, "Mix" ) ) ).thenReturn( playlist() );

        mvc.perform( json( post( "/api/music/playlists" ), "{\"url\":\"" + PLAYLIST + "\",\"name\":\"Mix\"}" ) )
                .andExpect( status().isCreated() )
                .andExpect( jsonPath( "$.id" ).value( 1 ) );
    }

    @Test
    void nomeDePlaylistMuitoLongoDevolve400() throws Exception
    {
        mvc.perform( json( post( "/api/music/playlists" ), "{\"url\":\"" + PLAYLIST + "\",\"name\":\"" + "a".repeat( 101 ) + "\"}" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "O nome pode ter no maximo 100 caracteres" ) );
    }

    @Test
    void playlistRepetidaDevolve409() throws Exception
    {
        when( musicService.addPlaylist( any() ) ).thenThrow( new ConflictException( "Essa playlist ja esta cadastrada" ) );

        mvc.perform( json( post( "/api/music/playlists" ), "{\"url\":\"" + PLAYLIST + "\"}" ) )
                .andExpect( status().isConflict() );
    }

    @Test
    void ligarDesligarAutomatico() throws Exception
    {
        when( musicService.setEnabled( 1L, false ) ).thenReturn( playlist() );

        mvc.perform( json( put( "/api/music/playlists/1" ), "{\"enabled\":false}" ) ).andExpect( status().isOk() );

        verify( musicService ).setEnabled( 1L, false );
    }

    @Test
    void ligarDesligarSemValorDevolve400() throws Exception
    {
        mvc.perform( json( put( "/api/music/playlists/1" ), "{}" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "Informe se a sincronizacao automatica fica ligada" ) );
    }

    @Test
    void excluirPlaylist() throws Exception
    {
        mvc.perform( delete( "/api/music/playlists/1" ) ).andExpect( status().isNoContent() );

        verify( musicService ).deletePlaylist( 1L );
    }

    @Test
    void sincronizarAgora() throws Exception
    {
        when( musicService.syncNow( 1L ) ).thenReturn( playlist() );

        mvc.perform( post( "/api/music/playlists/1/sync" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$.lastSyncMessage" ).value( "Nenhuma faixa nova" ) );
    }
}

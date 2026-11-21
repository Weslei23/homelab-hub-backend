package com.homelab.homepage.domain.music.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homelab.homepage.domain.music.dto.MusicDtos.DownloadRequest;
import com.homelab.homepage.domain.music.dto.MusicDtos.DownloadResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.MusicStatusResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistRequest;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistResponse;
import com.homelab.homepage.domain.music.dto.MusicDtos.PlaylistUpdateRequest;
import com.homelab.homepage.domain.music.dto.MusicDtos.QueueItemResponse;
import com.homelab.homepage.domain.music.service.MusicService;

/** Downloads de musica pelo Downtify. Todas as rotas exigem ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping( "/api/music" )
public class MusicController
{
    private final MusicService musicService;

    public MusicController( MusicService musicService )
    {
        this.musicService = musicService;
    }

    @GetMapping( "/status" )
    public MusicStatusResponse status()
    {
        return musicService.status();
    }

    @PostMapping( "/downloads" )
    @ResponseStatus( HttpStatus.ACCEPTED )
    public DownloadResponse download( @Valid @RequestBody DownloadRequest request )
    {
        return musicService.download( request.url() );
    }

    @GetMapping( "/queue" )
    public List<QueueItemResponse> queue()
    {
        return musicService.queue();
    }

    @GetMapping( "/playlists" )
    public List<PlaylistResponse> listPlaylists()
    {
        return musicService.listPlaylists();
    }

    @PostMapping( "/playlists" )
    @ResponseStatus( HttpStatus.CREATED )
    public PlaylistResponse addPlaylist( @Valid @RequestBody PlaylistRequest request )
    {
        return musicService.addPlaylist( request );
    }

    @PutMapping( "/playlists/{id}" )
    public PlaylistResponse updatePlaylist( @PathVariable Long id, @Valid @RequestBody PlaylistUpdateRequest request )
    {
        return musicService.setEnabled( id, request.enabled() );
    }

    @DeleteMapping( "/playlists/{id}" )
    @ResponseStatus( HttpStatus.NO_CONTENT )
    public void deletePlaylist( @PathVariable Long id )
    {
        musicService.deletePlaylist( id );
    }

    @PostMapping( "/playlists/{id}/sync" )
    public PlaylistResponse syncPlaylist( @PathVariable Long id )
    {
        return musicService.syncNow( id );
    }
}

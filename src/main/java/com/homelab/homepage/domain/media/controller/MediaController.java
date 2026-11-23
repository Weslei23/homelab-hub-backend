package com.homelab.homepage.domain.media.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homelab.homepage.domain.media.dto.MediaDtos.MediaOverview;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequest;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequestResponse;
import com.homelab.homepage.domain.media.dto.MediaDtos.SearchResult;
import com.homelab.homepage.domain.media.service.MediaService;

/**
 * Filmes e series (Sonarr, Radarr e Jellyfin). O painel ({@code /overview}) e para qualquer usuario logado;
 * buscar e pedir titulos exige ADMIN (ver SecurityConfig).
 */
@RestController
@RequestMapping( "/api/media" )
public class MediaController
{
    private final MediaService mediaService;

    public MediaController( MediaService mediaService )
    {
        this.mediaService = mediaService;
    }

    /** ADMIN ve tudo; USER nao ve enderecos internos nem o detalhe das falhas. */
    @GetMapping( "/overview" )
    public MediaOverview overview( Authentication authentication )
    {
        MediaOverview overview = mediaService.overview();
        return isAdmin( authentication ) ? overview : MediaService.forViewer( overview );
    }

    private static boolean isAdmin( Authentication authentication )
    {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch( a -> "ROLE_ADMIN".equals( a.getAuthority() ) );
    }

    @GetMapping( "/search" )
    public List<SearchResult> search( @RequestParam String type, @RequestParam String term )
    {
        return mediaService.search( type, term );
    }

    @PostMapping( "/requests" )
    @ResponseStatus( HttpStatus.CREATED )
    public MediaRequestResponse request( @Valid @RequestBody MediaRequest request )
    {
        return mediaService.request( request );
    }
}

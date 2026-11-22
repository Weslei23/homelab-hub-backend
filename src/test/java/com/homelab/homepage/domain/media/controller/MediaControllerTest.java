package com.homelab.homepage.domain.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.common.exceptions.ConflictException;
import com.homelab.homepage.common.exceptions.GlobalExceptionHandler;
import com.homelab.homepage.domain.media.client.MediaException;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaOverview;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequest;
import com.homelab.homepage.domain.media.dto.MediaDtos.MediaRequestResponse;
import com.homelab.homepage.domain.media.dto.MediaDtos.QueueItem;
import com.homelab.homepage.domain.media.dto.MediaDtos.SearchResult;
import com.homelab.homepage.domain.media.dto.MediaDtos.ServiceInfo;
import com.homelab.homepage.domain.media.dto.MediaDtos.Services;
import com.homelab.homepage.domain.media.dto.MediaDtos.UpcomingItem;
import com.homelab.homepage.domain.media.service.MediaService;

class MediaControllerTest
{
    private MediaService mediaService;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        mediaService = mock( MediaService.class );
        mvc = MockMvcBuilders.standaloneSetup( new MediaController( mediaService ) )
                .setControllerAdvice( new GlobalExceptionHandler() )
                .build();
    }

    @Test
    void painel() throws Exception
    {
        ServiceInfo online = new ServiceInfo( true, true, "4.0", "http://nas:8989" );
        when( mediaService.overview() ).thenReturn( new MediaOverview( new Services( online, ServiceInfo.notConfigured(), online ),
                List.of( new QueueItem( "episode", "Reacher", "S03E05", "downloading", 42.5, "00:10:00", null ) ),
                List.of( new UpcomingItem( "episode", "Lupin", "S04E01", Instant.parse( "2026-11-25T02:00:00Z" ), false ) ),
                List.of(), List.of(), List.of( "Radarr fora" ) ) );

        mvc.perform( get( "/api/media/overview" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$.services.sonarr.version" ).value( "4.0" ) )
                .andExpect( jsonPath( "$.services.radarr.configured" ).value( false ) )
                .andExpect( jsonPath( "$.queue[0].progress" ).value( 42.5 ) )
                .andExpect( jsonPath( "$.upcoming[0].date" ).value( "2026-11-25T02:00:00Z" ) )
                .andExpect( jsonPath( "$.errors[0]" ).value( "Radarr fora" ) );
    }

    @Test
    void busca() throws Exception
    {
        when( mediaService.search( "movie", "Duna" ) ).thenReturn(
                List.of( new SearchResult( "movie", 438631, "Duna", 2021, "Paul...", "p.jpg", false ) ) );

        mvc.perform( get( "/api/media/search" ).param( "type", "movie" ).param( "term", "Duna" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$[0].id" ).value( 438631 ) )
                .andExpect( jsonPath( "$[0].inLibrary" ).value( false ) );
    }

    @Test
    void buscaSemTermo() throws Exception
    {
        mvc.perform( get( "/api/media/search" ).param( "type", "movie" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "Parametro obrigatorio ausente: term" ) );
    }

    @Test
    void buscaInvalidaDevolve400() throws Exception
    {
        when( mediaService.search( "movie", "a" ) ).thenThrow( new BadRequestException( "Digite pelo menos 2 letras para buscar" ) );

        mvc.perform( get( "/api/media/search" ).param( "type", "movie" ).param( "term", "a" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "Digite pelo menos 2 letras para buscar" ) );
    }

    @Test
    void falhaNoArrDevolve502() throws Exception
    {
        when( mediaService.search( "series", "Lupin" ) ).thenThrow( new MediaException( "Sonarr indisponivel em http://nas:8989" ) );

        mvc.perform( get( "/api/media/search" ).param( "type", "series" ).param( "term", "Lupin" ) )
                .andExpect( status().isBadGateway() )
                .andExpect( jsonPath( "$.message" ).value( "Sonarr indisponivel em http://nas:8989" ) );
    }

    @Test
    void pedidoDevolve201() throws Exception
    {
        when( mediaService.request( new MediaRequest( "series", 366924L, "future" ) ) )
                .thenReturn( new MediaRequestResponse( "series", "Reacher", "Sonarr adicionou \"Reacher\" e ja esta procurando" ) );

        mvc.perform( post( "/api/media/requests" ).contentType( MediaType.APPLICATION_JSON )
                .content( "{\"type\":\"series\",\"id\":366924,\"monitor\":\"future\"}" ) )
                .andExpect( status().isCreated() )
                .andExpect( jsonPath( "$.title" ).value( "Reacher" ) );
    }

    @Test
    void pedidoRepetidoDevolve409() throws Exception
    {
        when( mediaService.request( any() ) ).thenThrow( new ConflictException( "\"Duna\" ja esta na biblioteca do Radarr" ) );

        mvc.perform( post( "/api/media/requests" ).contentType( MediaType.APPLICATION_JSON )
                .content( "{\"type\":\"movie\",\"id\":1}" ) )
                .andExpect( status().isConflict() );
    }

    @ParameterizedTest
    @CsvSource( delimiter = '|', value = {
            "{\"id\":1}|Informe se e filme ou serie",
            "{\"type\":\"musica\",\"id\":1}|O tipo deve ser movie ou series",
            "{\"type\":\"movie\"}|Escolha um resultado da busca",
            "{\"type\":\"movie\",\"id\":0}|Escolha um resultado da busca",
            "{\"type\":\"series\",\"id\":1,\"monitor\":\"tudo\"}|Opcao de episodios invalida" } )
    void pedidoInvalido( String body, String message ) throws Exception
    {
        mvc.perform( post( "/api/media/requests" ).contentType( MediaType.APPLICATION_JSON ).content( body ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( message ) );
        verify( mediaService, never() ).request( any() );
    }
}

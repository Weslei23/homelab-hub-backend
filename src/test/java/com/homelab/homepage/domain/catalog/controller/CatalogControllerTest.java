package com.homelab.homepage.domain.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.homelab.homepage.common.exceptions.GlobalExceptionHandler;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.catalog.dto.request.CardRequest;
import com.homelab.homepage.domain.catalog.dto.request.CategoryRequest;
import com.homelab.homepage.domain.catalog.dto.response.CardResponse;
import com.homelab.homepage.domain.catalog.dto.response.CategoryResponse;
import com.homelab.homepage.domain.catalog.service.CatalogService;

class CatalogControllerTest
{
    private CatalogService catalogService;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        catalogService = mock( CatalogService.class );
        mvc = MockMvcBuilders.standaloneSetup( new CatalogController( catalogService ) )
                .setControllerAdvice( new GlobalExceptionHandler() )
                .build();
    }

    private static MockHttpServletRequestBuilder json( MockHttpServletRequestBuilder builder, String body )
    {
        return builder.contentType( MediaType.APPLICATION_JSON ).content( body );
    }

    @Test
    void listaOCatalogo() throws Exception
    {
        when( catalogService.listCatalog() ).thenReturn( List.of(
                new CategoryResponse( 1L, "Infra", List.of(
                        new CardResponse( 10L, "Proxmox", "https://pve", "Virtualização", "proxmox" ) ) ) ) );

        mvc.perform( get( "/api/catalog" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$[0].id" ).value( 1 ) )
                .andExpect( jsonPath( "$[0].label" ).value( "Infra" ) )
                .andExpect( jsonPath( "$[0].services[0].name" ).value( "Proxmox" ) )
                .andExpect( jsonPath( "$[0].services[0].url" ).value( "https://pve" ) )
                .andExpect( jsonPath( "$[0].services[0].icon" ).value( "proxmox" ) );
    }

    @Test
    void criaCategoria() throws Exception
    {
        when( catalogService.createCategory( new CategoryRequest( "Rede" ) ) )
                .thenReturn( new CategoryResponse( 3L, "Rede", List.of() ) );

        mvc.perform( json( post( "/api/categories" ), "{\"label\":\"Rede\"}" ) )
                .andExpect( status().isCreated() )
                .andExpect( jsonPath( "$.id" ).value( 3 ) )
                .andExpect( jsonPath( "$.services" ).isEmpty() );
    }

    @Test
    void criarCategoriaSemNomeDevolve400() throws Exception
    {
        mvc.perform( json( post( "/api/categories" ), "{\"label\":\"\"}" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "O nome da categoria e obrigatorio" ) );
        verify( catalogService, never() ).createCategory( any() );
    }

    @Test
    void renomeiaCategoria() throws Exception
    {
        when( catalogService.renameCategory( 1L, new CategoryRequest( "Servidores" ) ) )
                .thenReturn( new CategoryResponse( 1L, "Servidores", List.of() ) );

        mvc.perform( json( put( "/api/categories/1" ), "{\"label\":\"Servidores\"}" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$.label" ).value( "Servidores" ) );
    }

    @Test
    void renomearCategoriaInexistenteDevolve404() throws Exception
    {
        when( catalogService.renameCategory( eq( 99L ), any() ) )
                .thenThrow( new ResourceNotFoundException( "Categoria nao encontrada" ) );

        mvc.perform( json( put( "/api/categories/99" ), "{\"label\":\"X\"}" ) )
                .andExpect( status().isNotFound() )
                .andExpect( jsonPath( "$.message" ).value( "Categoria nao encontrada" ) );
    }

    @Test
    void excluiCategoria() throws Exception
    {
        mvc.perform( delete( "/api/categories/1" ) ).andExpect( status().isNoContent() );

        verify( catalogService ).deleteCategory( 1L );
    }

    @Test
    void criaCard() throws Exception
    {
        CardRequest request = new CardRequest( "Proxmox", "https://pve", null, "proxmox" );
        when( catalogService.createCard( 1L, request ) )
                .thenReturn( new CardResponse( 10L, "Proxmox", "https://pve", null, "proxmox" ) );

        mvc.perform( json( post( "/api/categories/1/cards" ),
                "{\"name\":\"Proxmox\",\"url\":\"https://pve\",\"icon\":\"proxmox\"}" ) )
                .andExpect( status().isCreated() )
                .andExpect( jsonPath( "$.id" ).value( 10 ) );
    }

    @Test
    void criarCardComUrlInvalidaDevolve400() throws Exception
    {
        mvc.perform( json( post( "/api/categories/1/cards" ), "{\"name\":\"X\",\"url\":\"pve.local\"}" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "A URL deve comecar com http:// ou https://" ) );
        verify( catalogService, never() ).createCard( any(), any() );
    }

    @Test
    void criarCardComJsonMalformadoDevolve400() throws Exception
    {
        mvc.perform( json( post( "/api/categories/1/cards" ), "{nao e json" ) )
                .andExpect( status().isBadRequest() );
    }

    @Test
    void atualizaCard() throws Exception
    {
        CardRequest request = new CardRequest( "PVE", "https://pve", "desc", null );
        when( catalogService.updateCard( 10L, request ) )
                .thenReturn( new CardResponse( 10L, "PVE", "https://pve", "desc", null ) );

        mvc.perform( json( put( "/api/cards/10" ), "{\"name\":\"PVE\",\"url\":\"https://pve\",\"description\":\"desc\"}" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$.name" ).value( "PVE" ) )
                .andExpect( jsonPath( "$.description" ).value( "desc" ) );
    }

    @Test
    void excluiCard() throws Exception
    {
        mvc.perform( delete( "/api/cards/10" ) ).andExpect( status().isNoContent() );

        verify( catalogService ).deleteCard( 10L );
    }

    @Test
    void excluirCardInexistenteDevolve404() throws Exception
    {
        doThrow( new ResourceNotFoundException( "Card nao encontrado" ) ).when( catalogService ).deleteCard( 99L );

        mvc.perform( delete( "/api/cards/99" ) )
                .andExpect( status().isNotFound() )
                .andExpect( jsonPath( "$.message" ).value( "Card nao encontrado" ) );
    }
}

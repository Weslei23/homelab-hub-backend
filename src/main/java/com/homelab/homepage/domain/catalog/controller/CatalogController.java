package com.homelab.homepage.domain.catalog.controller;

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

import com.homelab.homepage.domain.catalog.dto.request.CardRequest;
import com.homelab.homepage.domain.catalog.dto.response.CardResponse;
import com.homelab.homepage.domain.catalog.dto.request.CategoryRequest;
import com.homelab.homepage.domain.catalog.dto.response.CategoryResponse;
import com.homelab.homepage.domain.catalog.service.CatalogService;

@RestController
@RequestMapping( "/api" )
public class CatalogController
{

    private final CatalogService catalogService;

    public CatalogController( CatalogService catalogService )
    {
        this.catalogService = catalogService;
    }

    @GetMapping( "/catalog" )
    public List<CategoryResponse> listCatalog()
    {
        return catalogService.listCatalog();
    }

    @PostMapping( "/categories" )
    @ResponseStatus( HttpStatus.CREATED )
    public CategoryResponse createCategory( @Valid @RequestBody CategoryRequest request )
    {
        return catalogService.createCategory( request );
    }

    @PutMapping( "/categories/{id}" )
    public CategoryResponse renameCategory( @PathVariable Long id, @Valid @RequestBody CategoryRequest request )
    {
        return catalogService.renameCategory( id, request );
    }

    @DeleteMapping( "/categories/{id}" )
    @ResponseStatus( HttpStatus.NO_CONTENT )
    public void deleteCategory( @PathVariable Long id )
    {
        catalogService.deleteCategory( id );
    }

    @PostMapping( "/categories/{id}/cards" )
    @ResponseStatus( HttpStatus.CREATED )
    public CardResponse createCard( @PathVariable Long id, @Valid @RequestBody CardRequest request )
    {
        return catalogService.createCard( id, request );
    }

    @PutMapping( "/cards/{id}" )
    public CardResponse updateCard( @PathVariable Long id, @Valid @RequestBody CardRequest request )
    {
        return catalogService.updateCard( id, request );
    }

    @DeleteMapping( "/cards/{id}" )
    @ResponseStatus( HttpStatus.NO_CONTENT )
    public void deleteCard( @PathVariable Long id )
    {
        catalogService.deleteCard( id );
    }
}

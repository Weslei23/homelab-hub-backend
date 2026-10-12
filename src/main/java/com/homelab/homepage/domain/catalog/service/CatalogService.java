package com.homelab.homepage.domain.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homelab.homepage.domain.catalog.dto.request.CardRequest;
import com.homelab.homepage.domain.catalog.dto.response.CardResponse;
import com.homelab.homepage.domain.catalog.dto.request.CategoryRequest;
import com.homelab.homepage.domain.catalog.dto.response.CategoryResponse;
import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.catalog.mapper.CatalogMapper;
import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;
import com.homelab.homepage.domain.catalog.repository.CardRepository;
import com.homelab.homepage.domain.catalog.repository.CategoryRepository;

@Service
@Transactional
public class CatalogService
{

    private final CategoryRepository categoryRepository;
    private final CardRepository cardRepository;

    public CatalogService( CategoryRepository categoryRepository, CardRepository cardRepository )
    {
        this.categoryRepository = categoryRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional( readOnly = true )
    public List<CategoryResponse> listCatalog()
    {
        return categoryRepository.findAllWithCards().stream()
                .map( CatalogMapper::toResponse )
                .toList();
    }

    public CategoryResponse createCategory( CategoryRequest request )
    {
        Category newCategory = new Category( request.label().trim(), categoryRepository.nextPosition() );
        Category savedCategory = categoryRepository.save( newCategory );
        return CatalogMapper.toResponse( savedCategory );
    }

    public CategoryResponse renameCategory( Long categoryId, CategoryRequest request )
    {
        Category category = findCategoryOrFail( categoryId );
        category.setLabel( request.label().trim() );
        return CatalogMapper.toResponse( category );
    }

    public void deleteCategory( Long categoryId )
    {
        categoryRepository.delete( findCategoryOrFail( categoryId ) );
    }

    public CardResponse createCard( Long categoryId, CardRequest request )
    {
        Category category = findCategoryOrFail( categoryId );
        Card newCard = new Card(
                category,
                request.name().trim(),
                request.url().trim(),
                blankToNull( request.description() ),
                blankToNull( request.icon() ),
                cardRepository.nextPosition( categoryId ) );
        Card savedCard = cardRepository.save( newCard );
        return CatalogMapper.toResponse( savedCard );
    }

    public CardResponse updateCard( Long cardId, CardRequest request )
    {
        Card card = findCardOrFail( cardId );
        card.updateData(
                request.name().trim(),
                request.url().trim(),
                blankToNull( request.description() ),
                blankToNull( request.icon() ) );
        return CatalogMapper.toResponse( card );
    }

    public void deleteCard( Long cardId )
    {
        cardRepository.delete( findCardOrFail( cardId ) );
    }

    private Category findCategoryOrFail( Long categoryId )
    {
        return categoryRepository.findById( categoryId )
                .orElseThrow( () -> new ResourceNotFoundException( "Categoria nao encontrada" ) );
    }

    private Card findCardOrFail( Long cardId )
    {
        return cardRepository.findById( cardId )
                .orElseThrow( () -> new ResourceNotFoundException( "Card nao encontrado" ) );
    }

    private static String blankToNull( String value )
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

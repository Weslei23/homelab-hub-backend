package com.homelab.homepage.mapper;

import java.util.List;

import com.homelab.homepage.dto.CardResponse;
import com.homelab.homepage.dto.CategoryResponse;
import com.homelab.homepage.model.Card;
import com.homelab.homepage.model.Category;

/** Converte entidades do dominio em DTOs de resposta da API. */
public final class CatalogMapper
{

    private CatalogMapper()
    {
    }

    public static CardResponse toResponse( Card card )
    {
        return new CardResponse( card.getId(), card.getName(), card.getUrl(), card.getDescription(), card.getIcon() );
    }

    public static CategoryResponse toResponse( Category category )
    {
        List<CardResponse> cardResponses = category.getCards().stream()
                .map( CatalogMapper::toResponse )
                .toList();
        return new CategoryResponse( category.getId(), category.getLabel(), cardResponses );
    }
}

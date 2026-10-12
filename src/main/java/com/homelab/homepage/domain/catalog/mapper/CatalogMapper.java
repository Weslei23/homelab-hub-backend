package com.homelab.homepage.domain.catalog.mapper;

import java.util.List;

import com.homelab.homepage.domain.catalog.dto.response.CardResponse;
import com.homelab.homepage.domain.catalog.dto.response.CategoryResponse;
import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;

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

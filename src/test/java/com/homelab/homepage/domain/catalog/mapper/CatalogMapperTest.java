package com.homelab.homepage.domain.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.domain.catalog.dto.response.CardResponse;
import com.homelab.homepage.domain.catalog.dto.response.CategoryResponse;
import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;
import com.homelab.homepage.support.TestData;

class CatalogMapperTest
{
    @Test
    void cardParaResponse()
    {
        Category category = TestData.category( 1L, "Infra" );
        Card card = new Card( category, "Proxmox", "https://pve.local", "Virtualização", "proxmox", 3 );
        ReflectionTestUtils.setField( card, "id", 10L );

        assertThat( CatalogMapper.toResponse( card ) )
                .isEqualTo( new CardResponse( 10L, "Proxmox", "https://pve.local", "Virtualização", "proxmox" ) );
    }

    @Test
    void categoriaParaResponseMantemAOrdemDosCards()
    {
        Category category = TestData.category( 1L, "Infra" );
        category.getCards().add( TestData.card( 2L, category, "B", "https://b" ) );
        category.getCards().add( TestData.card( 1L, category, "A", "https://a" ) );

        CategoryResponse response = CatalogMapper.toResponse( category );

        assertThat( response.id() ).isEqualTo( 1L );
        assertThat( response.label() ).isEqualTo( "Infra" );
        assertThat( response.services() ).extracting( CardResponse::name ).containsExactly( "B", "A" );
    }

    @Test
    void categoriaSemCards()
    {
        assertThat( CatalogMapper.toResponse( TestData.category( 1L, "Vazia" ) ).services() ).isEmpty();
    }
}

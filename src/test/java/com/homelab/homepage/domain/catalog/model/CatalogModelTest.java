package com.homelab.homepage.domain.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogModelTest
{
    @Test
    void cardGuardaOsDadosDoConstrutor()
    {
        Category category = new Category( "Infra", 2 );
        Card card = new Card( category, "Proxmox", "https://pve", "desc", "proxmox", 5 );

        assertThat( card.getCategory() ).isSameAs( category );
        assertThat( card.getName() ).isEqualTo( "Proxmox" );
        assertThat( card.getUrl() ).isEqualTo( "https://pve" );
        assertThat( card.getDescription() ).isEqualTo( "desc" );
        assertThat( card.getIcon() ).isEqualTo( "proxmox" );
        assertThat( card.getPosition() ).isEqualTo( 5 );
        assertThat( card.getId() ).isNull();
    }

    @Test
    void updateDataTrocaOsCamposEditaveisEMantemAPosicao()
    {
        Category category = new Category( "Infra", 0 );
        Card card = new Card( category, "A", "https://a", "x", "a", 7 );

        card.updateData( "B", "https://b", null, null );

        assertThat( card.getName() ).isEqualTo( "B" );
        assertThat( card.getUrl() ).isEqualTo( "https://b" );
        assertThat( card.getDescription() ).isNull();
        assertThat( card.getIcon() ).isNull();
        assertThat( card.getPosition() ).isEqualTo( 7 );
        assertThat( card.getCategory() ).isSameAs( category );
    }

    @Test
    void setUrl()
    {
        Card card = new Card( null, "A", "https://a", null, null, 0 );

        card.setUrl( "https://b" );

        assertThat( card.getUrl() ).isEqualTo( "https://b" );
    }

    @Test
    void categoriaComecaSemCards()
    {
        Category category = new Category( "Infra", 3 );

        assertThat( category.getLabel() ).isEqualTo( "Infra" );
        assertThat( category.getPosition() ).isEqualTo( 3 );
        assertThat( category.getCards() ).isEmpty();
        assertThat( category.getId() ).isNull();
    }

    @Test
    void renomearCategoria()
    {
        Category category = new Category( "Infra", 0 );

        category.setLabel( "Servidores" );

        assertThat( category.getLabel() ).isEqualTo( "Servidores" );
    }
}

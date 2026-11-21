package com.homelab.homepage.domain.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.common.exceptions.ResourceNotFoundException;
import com.homelab.homepage.domain.catalog.dto.request.CardRequest;
import com.homelab.homepage.domain.catalog.dto.request.CategoryRequest;
import com.homelab.homepage.domain.catalog.dto.response.CardResponse;
import com.homelab.homepage.domain.catalog.dto.response.CategoryResponse;
import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;
import com.homelab.homepage.domain.catalog.repository.CardRepository;
import com.homelab.homepage.domain.catalog.repository.CategoryRepository;
import com.homelab.homepage.domain.status.service.StatusService;
import com.homelab.homepage.support.TestData;

@ExtendWith( MockitoExtension.class )
class CatalogServiceTest
{
    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private StatusService statusService;

    @InjectMocks
    private CatalogService catalogService;

    private Category infra;

    @BeforeEach
    void setUp()
    {
        infra = TestData.category( 1L, "Infraestrutura" );
    }

    @Nested
    class Listagem
    {
        @Test
        void listaCategoriasComSeusCards()
        {
            infra.getCards().add( TestData.card( 10L, infra, "Proxmox", "https://pve.local:8006" ) );
            Category media = TestData.category( 2L, "Mídia" );
            when( categoryRepository.findAllWithCards() ).thenReturn( List.of( infra, media ) );

            List<CategoryResponse> catalog = catalogService.listCatalog();

            assertThat( catalog ).extracting( CategoryResponse::label ).containsExactly( "Infraestrutura", "Mídia" );
            assertThat( catalog.getFirst().services() ).extracting( CardResponse::name ).containsExactly( "Proxmox" );
            assertThat( catalog.get( 1 ).services() ).isEmpty();
        }

        @Test
        void catalogoVazio()
        {
            when( categoryRepository.findAllWithCards() ).thenReturn( List.of() );

            assertThat( catalogService.listCatalog() ).isEmpty();
        }
    }

    @Nested
    class Categorias
    {
        @Test
        void criaComNomeSemEspacosNaProximaPosicao()
        {
            when( categoryRepository.nextPosition() ).thenReturn( 4 );
            when( categoryRepository.save( any( Category.class ) ) ).thenAnswer( invocation -> invocation.getArgument( 0 ) );

            CategoryResponse response = catalogService.createCategory( new CategoryRequest( "  Rede  " ) );

            ArgumentCaptor<Category> saved = ArgumentCaptor.forClass( Category.class );
            verify( categoryRepository ).save( saved.capture() );
            assertThat( saved.getValue().getLabel() ).isEqualTo( "Rede" );
            assertThat( saved.getValue().getPosition() ).isEqualTo( 4 );
            assertThat( response.label() ).isEqualTo( "Rede" );
            assertThat( response.services() ).isEmpty();
        }

        @Test
        void renomeia()
        {
            when( categoryRepository.findById( 1L ) ).thenReturn( Optional.of( infra ) );

            CategoryResponse response = catalogService.renameCategory( 1L, new CategoryRequest( " Servidores " ) );

            assertThat( infra.getLabel() ).isEqualTo( "Servidores" );
            assertThat( response.id() ).isEqualTo( 1L );
            assertThat( response.label() ).isEqualTo( "Servidores" );
        }

        @Test
        void renomearInexistenteFalha()
        {
            when( categoryRepository.findById( 99L ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> catalogService.renameCategory( 99L, new CategoryRequest( "X" ) ) )
                    .isInstanceOf( ResourceNotFoundException.class )
                    .hasMessage( "Categoria nao encontrada" );
        }

        @Test
        void excluiERemoveOStatusDosCards()
        {
            infra.getCards().add( TestData.card( 10L, infra, "Proxmox", "https://pve.local" ) );
            infra.getCards().add( TestData.card( 11L, infra, "TrueNAS", "https://nas.local" ) );
            when( categoryRepository.findById( 1L ) ).thenReturn( Optional.of( infra ) );

            catalogService.deleteCategory( 1L );

            verify( categoryRepository ).delete( infra );
            verify( statusService ).evict( 10L );
            verify( statusService ).evict( 11L );
        }

        @Test
        void excluirInexistenteFalha()
        {
            when( categoryRepository.findById( 99L ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> catalogService.deleteCategory( 99L ) )
                    .isInstanceOf( ResourceNotFoundException.class );
            verify( categoryRepository, never() ).delete( any() );
        }
    }

    @Nested
    class Cards
    {
        private void saveAssignsId( long id )
        {
            when( cardRepository.save( any( Card.class ) ) ).thenAnswer( invocation -> {
                Card card = invocation.getArgument( 0 );
                ReflectionTestUtils.setField( card, "id", id );
                return card;
            } );
        }

        @Test
        void criaNormalizandoOsCampos()
        {
            when( categoryRepository.findById( 1L ) ).thenReturn( Optional.of( infra ) );
            when( cardRepository.nextPosition( 1L ) ).thenReturn( 2 );
            saveAssignsId( 50L );

            CardResponse response = catalogService.createCard( 1L,
                    new CardRequest( "  Proxmox ", " https://pve.local:8006 ", "  Virtualização ", " proxmox " ) );

            ArgumentCaptor<Card> saved = ArgumentCaptor.forClass( Card.class );
            verify( cardRepository ).save( saved.capture() );
            Card card = saved.getValue();
            assertThat( card.getCategory() ).isSameAs( infra );
            assertThat( card.getName() ).isEqualTo( "Proxmox" );
            assertThat( card.getUrl() ).isEqualTo( "https://pve.local:8006" );
            assertThat( card.getDescription() ).isEqualTo( "Virtualização" );
            assertThat( card.getIcon() ).isEqualTo( "proxmox" );
            assertThat( card.getPosition() ).isEqualTo( 2 );
            assertThat( response ).isEqualTo(
                    new CardResponse( 50L, "Proxmox", "https://pve.local:8006", "Virtualização", "proxmox" ) );
        }

        @Test
        void descricaoEIconeEmBrancoViramNulos()
        {
            when( categoryRepository.findById( 1L ) ).thenReturn( Optional.of( infra ) );
            saveAssignsId( 50L );

            CardResponse response = catalogService.createCard( 1L, new CardRequest( "X", "https://x", "   ", "" ) );

            assertThat( response.description() ).isNull();
            assertThat( response.icon() ).isNull();
        }

        @Test
        void descricaoEIconeNulosContinuamNulos()
        {
            when( categoryRepository.findById( 1L ) ).thenReturn( Optional.of( infra ) );
            saveAssignsId( 50L );

            CardResponse response = catalogService.createCard( 1L, new CardRequest( "X", "https://x", null, null ) );

            assertThat( response.description() ).isNull();
            assertThat( response.icon() ).isNull();
        }

        @Test
        void criarVerificaOStatusNaHora()
        {
            when( categoryRepository.findById( 1L ) ).thenReturn( Optional.of( infra ) );
            saveAssignsId( 50L );

            catalogService.createCard( 1L, new CardRequest( "X", "https://x.local", null, null ) );

            verify( statusService ).checkSoon( 50L, "https://x.local" );
        }

        @Test
        void criarEmCategoriaInexistenteFalha()
        {
            when( categoryRepository.findById( 99L ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> catalogService.createCard( 99L, new CardRequest( "X", "https://x", null, null ) ) )
                    .isInstanceOf( ResourceNotFoundException.class )
                    .hasMessage( "Categoria nao encontrada" );
            verify( cardRepository, never() ).save( any() );
            verify( statusService, never() ).checkSoon( anyLong(), any() );
        }

        @Test
        void atualizaEVerificaOStatusNaHora()
        {
            Card card = TestData.card( 10L, infra, "Proxmox", "https://antigo" );
            when( cardRepository.findById( 10L ) ).thenReturn( Optional.of( card ) );

            CardResponse response = catalogService.updateCard( 10L,
                    new CardRequest( " PVE ", " https://novo ", " desc ", " " ) );

            assertThat( response ).isEqualTo( new CardResponse( 10L, "PVE", "https://novo", "desc", null ) );
            assertThat( card.getName() ).isEqualTo( "PVE" );
            verify( statusService ).checkSoon( 10L, "https://novo" );
        }

        @Test
        void atualizarInexistenteFalha()
        {
            when( cardRepository.findById( 99L ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> catalogService.updateCard( 99L, new CardRequest( "X", "https://x", null, null ) ) )
                    .isInstanceOf( ResourceNotFoundException.class )
                    .hasMessage( "Card nao encontrado" );
            verify( statusService, never() ).checkSoon( anyLong(), any() );
        }

        @Test
        void excluiERemoveOStatus()
        {
            Card card = TestData.card( 10L, infra, "Proxmox", "https://pve" );
            when( cardRepository.findById( 10L ) ).thenReturn( Optional.of( card ) );

            catalogService.deleteCard( 10L );

            verify( cardRepository ).delete( card );
            verify( statusService ).evict( 10L );
        }

        @Test
        void excluirInexistenteFalha()
        {
            when( cardRepository.findById( 99L ) ).thenReturn( Optional.empty() );

            assertThatThrownBy( () -> catalogService.deleteCard( 99L ) )
                    .isInstanceOf( ResourceNotFoundException.class );
            verify( cardRepository, never() ).delete( any() );
            verify( statusService, never() ).evict( any() );
        }
    }
}

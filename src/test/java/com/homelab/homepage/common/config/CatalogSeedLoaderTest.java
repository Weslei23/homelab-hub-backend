package com.homelab.homepage.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;
import com.homelab.homepage.domain.catalog.repository.CategoryRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CatalogSeedLoaderTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CategoryRepository categoryRepository;
    private JdbcTemplate jdbcTemplate;
    private PlatformTransactionManager transactionManager;
    private CatalogSeedLoader loader;

    @BeforeEach
    void setUp()
    {
        categoryRepository = mock( CategoryRepository.class );
        jdbcTemplate = mock( JdbcTemplate.class );
        transactionManager = mock( PlatformTransactionManager.class );
        loader = new CatalogSeedLoader( categoryRepository, JSON, jdbcTemplate, new TransactionTemplate( transactionManager ) );
    }

    private static JsonNode seedFile() throws Exception
    {
        try ( InputStream in = new ClassPathResource( "seed-catalog.json" ).getInputStream() )
        {
            return JSON.readTree( in );
        }
    }

    private List<Category> savedCategories()
    {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass( Category.class );
        verify( categoryRepository, org.mockito.Mockito.atLeastOnce() ).save( captor.capture() );
        return captor.getAllValues();
    }

    /** Simula uma tabela legada com um link salvo. */
    private void legacyTable( String table, String serviceName, String url )
    {
        doAnswer( invocation -> {
            ResultSet row = mock( ResultSet.class );
            when( row.getString( 1 ) ).thenReturn( serviceName );
            when( row.getString( 2 ) ).thenReturn( url );
            invocation.<RowCallbackHandler> getArgument( 1 ).processRow( row );
            return null;
        } ).when( jdbcTemplate ).query( eq( "select service_name, url from " + table ), any( RowCallbackHandler.class ) );
    }

    @Test
    void bancoComCategoriasNaoECarregado() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 3L );

        loader.run( new DefaultApplicationArguments() );

        verify( categoryRepository, never() ).save( any() );
        verify( jdbcTemplate, never() ).query( anyString(), any( RowCallbackHandler.class ) );
    }

    @Test
    void bancoVazioRecebeTodoOCatalogoPadrao() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 0L );
        JsonNode seed = seedFile();

        loader.run( new DefaultApplicationArguments() );

        List<Category> saved = savedCategories();
        verify( categoryRepository, times( seed.size() ) ).save( any() );
        for ( int i = 0; i < seed.size(); i++ )
        {
            Category category = saved.get( i );
            JsonNode seedCategory = seed.get( i );
            assertThat( category.getLabel() ).isEqualTo( seedCategory.get( "label" ).asString() );
            assertThat( category.getPosition() ).isEqualTo( i );
            assertThat( category.getCards() ).hasSize( seedCategory.get( "services" ).size() );
        }
    }

    @Test
    void cardsFicamNaOrdemDoArquivoEApontamParaACategoria() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 0L );
        JsonNode firstSeedCard = seedFile().get( 0 ).get( "services" ).get( 0 );

        loader.run( new DefaultApplicationArguments() );

        Category first = savedCategories().getFirst();
        for ( int i = 0; i < first.getCards().size(); i++ )
        {
            Card card = first.getCards().get( i );
            assertThat( card.getPosition() ).isEqualTo( i );
            assertThat( card.getCategory() ).isSameAs( first );
        }
        Card card = first.getCards().getFirst();
        assertThat( card.getName() ).isEqualTo( firstSeedCard.get( "name" ).asString() );
        assertThat( card.getUrl() ).isEqualTo( firstSeedCard.get( "url" ).asString() );
        assertThat( card.getIcon() ).isEqualTo( firstSeedCard.get( "icon" ).asString() );
        assertThat( card.getDescription() ).isEqualTo( firstSeedCard.get( "description" ).asString() );
    }

    @Test
    void rodaDentroDeUmaTransacao() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 0L );

        loader.run( new DefaultApplicationArguments() );

        verify( transactionManager ).getTransaction( any() );
        verify( transactionManager ).commit( any() );
    }

    @Test
    void linksDasTabelasLegadasSubstituemOsDoArquivo() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 0L );
        String firstName = seedFile().get( 0 ).get( "services" ).get( 0 ).get( "name" ).asString();
        legacyTable( "service_link", firstName, "https://legado.local" );
        doThrow( new BadSqlGrammarException( "select", "select ...", new java.sql.SQLException( "nao existe" ) ) )
                .when( jdbcTemplate ).query( eq( "select service_name, url from tb_service_link" ), any( RowCallbackHandler.class ) );

        loader.run( new DefaultApplicationArguments() );

        Card first = savedCategories().getFirst().getCards().getFirst();
        assertThat( first.getUrl() ).isEqualTo( "https://legado.local" );
    }

    @Test
    void linksLegadosDeServicosDesconhecidosSaoIgnorados() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 0L );
        legacyTable( "tb_service_link", "Servico Que Nao Existe", "https://x" );

        loader.run( new DefaultApplicationArguments() );

        assertThat( savedCategories().stream().flatMap( c -> c.getCards().stream() ) )
                .noneMatch( card -> card.getUrl().equals( "https://x" ) );
    }

    @Test
    void semTabelasLegadasUsaOsLinksDoArquivo() throws Exception
    {
        when( categoryRepository.count() ).thenReturn( 0L );
        doThrow( new BadSqlGrammarException( "select", "select ...", new java.sql.SQLException( "nao existe" ) ) )
                .when( jdbcTemplate ).query( anyString(), any( RowCallbackHandler.class ) );
        String firstUrl = seedFile().get( 0 ).get( "services" ).get( 0 ).get( "url" ).asString();

        loader.run( new DefaultApplicationArguments() );

        assertThat( savedCategories().getFirst().getCards().getFirst().getUrl() ).isEqualTo( firstUrl );
    }
}

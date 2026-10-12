package com.homelab.homepage.common.config;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;
import com.homelab.homepage.domain.catalog.repository.CategoryRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogSeedLoader implements ApplicationRunner
{

    private static final Logger log = LoggerFactory.getLogger( CatalogSeedLoader.class );

    private record SeedCard( String name, String url, String description, String icon )
    {
    }

    private record SeedCategory( String label, List<SeedCard> services )
    {
    }

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public CatalogSeedLoader( CategoryRepository categoryRepository, ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate )
    {
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run( ApplicationArguments args ) throws Exception
    {
        if ( categoryRepository.count() > 0 )
        {
            return;
        }

        List<SeedCategory> seedCategories = readSeedFile();
        Map<String, String> legacyLinks = loadLegacyLinks();

        transactionTemplate.executeWithoutResult( status -> {
            int categoryPosition = 0;
            for ( SeedCategory seedCategory : seedCategories )
            {
                Category category = new Category( seedCategory.label(), categoryPosition++ );
                int cardPosition = 0;
                for ( SeedCard seedCard : seedCategory.services() )
                {
                    String url = legacyLinks.getOrDefault( seedCard.name(), seedCard.url() );
                    category.getCards().add( new Card( category, seedCard.name(), url,
                            seedCard.description(), seedCard.icon(), cardPosition++ ) );
                }
                categoryRepository.save( category );
            }
        } );
        log.info( "Catalogo padrao carregado ({} categorias)", seedCategories.size() );
    }

    private List<SeedCategory> readSeedFile() throws Exception
    {
        try ( InputStream inputStream = new ClassPathResource( "seed-catalog.json" ).getInputStream() )
        {
            return objectMapper.readValue( inputStream, new TypeReference<List<SeedCategory>>()
            {
            } );
        }
    }

    private Map<String, String> loadLegacyLinks()
    {
        Map<String, String> links = new HashMap<>();
        for ( String table : List.of( "service_link", "tb_service_link" ) )
        {
            try
            {
                jdbcTemplate.query( "select service_name, url from " + table, resultSet -> {
                    links.put( resultSet.getString( 1 ), resultSet.getString( 2 ) );
                } );
            }
            catch ( Exception ignored )
            {
                // tabela inexistente: nada a migrar
            }
        }
        return links;
    }
}

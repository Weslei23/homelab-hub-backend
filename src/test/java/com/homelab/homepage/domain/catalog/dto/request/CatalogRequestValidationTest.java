package com.homelab.homepage.domain.catalog.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogRequestValidationTest
{
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp()
    {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown()
    {
        factory.close();
    }

    private static <T> Set<String> messages( T request )
    {
        return validator.validate( request ).stream()
                .map( ConstraintViolation::getMessage )
                .collect( Collectors.toSet() );
    }

    private static CardRequest card( String name, String url, String description, String icon )
    {
        return new CardRequest( name, url, description, icon );
    }

    @Nested
    class Card
    {
        @Test
        void cardCompletoEValido()
        {
            assertThat( messages( card( "Proxmox", "https://pve.local:8006", "Virtualização", "proxmox" ) ) ).isEmpty();
        }

        @Test
        void descricaoEIconeSaoOpcionais()
        {
            assertThat( messages( card( "Proxmox", "http://pve.local", null, null ) ) ).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource( strings = { "   " } )
        void nomeObrigatorio( String name )
        {
            assertThat( messages( card( name, "https://x", null, null ) ) ).contains( "O nome e obrigatorio" );
        }

        @Test
        void nomeComNoMaximo100Caracteres()
        {
            assertThat( messages( card( "a".repeat( 100 ), "https://x", null, null ) ) ).isEmpty();
            assertThat( messages( card( "a".repeat( 101 ), "https://x", null, null ) ) )
                    .containsExactly( "O nome pode ter no maximo 100 caracteres" );
        }

        @ParameterizedTest
        @NullAndEmptySource
        void urlObrigatoria( String url )
        {
            assertThat( messages( card( "X", url, null, null ) ) ).contains( "A URL e obrigatoria" );
        }

        @ParameterizedTest
        @ValueSource( strings = { "http://pve.local", "https://pve.local:8006/path?x=1", "HTTPS://PVE.LOCAL", "http://192.168.3.183:8096" } )
        void urlsHttpValidas( String url )
        {
            assertThat( messages( card( "X", url, null, null ) ) ).isEmpty();
        }

        @ParameterizedTest
        @ValueSource( strings = { "pve.local", "ftp://nas.local", "#", "https://", "https://com espaco", "javascript:alert(1)" } )
        void urlsInvalidas( String url )
        {
            assertThat( messages( card( "X", url, null, null ) ) ).contains( "A URL deve comecar com http:// ou https://" );
        }

        @Test
        void urlComNoMaximo2048Caracteres()
        {
            String base = "https://x/";
            assertThat( messages( card( "X", base + "a".repeat( 2048 - base.length() ), null, null ) ) ).isEmpty();
            assertThat( messages( card( "X", base + "a".repeat( 2049 - base.length() ), null, null ) ) )
                    .contains( "A URL pode ter no maximo 2048 caracteres" );
        }

        @Test
        void descricaoComNoMaximo255Caracteres()
        {
            assertThat( messages( card( "X", "https://x", "a".repeat( 255 ), null ) ) ).isEmpty();
            assertThat( messages( card( "X", "https://x", "a".repeat( 256 ), null ) ) )
                    .containsExactly( "A descricao pode ter no maximo 255 caracteres" );
        }

        @ParameterizedTest
        @ValueSource( strings = { "proxmox", "nginx-proxy-manager", "Uptime-Kuma", "https://cdn.exemplo.com/icone.png" } )
        void iconesValidos( String icon )
        {
            assertThat( messages( card( "X", "https://x", null, icon ) ) ).isEmpty();
        }

        @ParameterizedTest
        @ValueSource( strings = { "icone com espaco", "icone_sublinhado", "../etc/passwd", "ftp://x/icone.png", "ícone" } )
        void iconesInvalidos( String icon )
        {
            assertThat( messages( card( "X", "https://x", null, icon ) ) ).containsExactly( "Icone invalido" );
        }

        @Test
        void chaveDeIconeComNoMaximo60Caracteres()
        {
            assertThat( messages( card( "X", "https://x", null, "a".repeat( 60 ) ) ) ).isEmpty();
            assertThat( messages( card( "X", "https://x", null, "a".repeat( 61 ) ) ) ).containsExactly( "Icone invalido" );
        }
    }

    @Nested
    class Category
    {
        @Test
        void categoriaValida()
        {
            assertThat( messages( new CategoryRequest( "Infraestrutura" ) ) ).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource( strings = { "   " } )
        void nomeObrigatorio( String label )
        {
            assertThat( messages( new CategoryRequest( label ) ) ).containsExactly( "O nome da categoria e obrigatorio" );
        }

        @Test
        void nomeComNoMaximo100Caracteres()
        {
            assertThat( messages( new CategoryRequest( "a".repeat( 100 ) ) ) ).isEmpty();
            assertThat( messages( new CategoryRequest( "a".repeat( 101 ) ) ) )
                    .containsExactly( "O nome da categoria pode ter no maximo 100 caracteres" );
        }
    }
}

package com.homelab.homepage.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.homelab.homepage.domain.user.model.JWTUserData;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.support.TestData;

class TokenConfigTest
{
    private static final String SECRET = "segredo-de-teste";

    private TokenConfig tokenConfig;

    @BeforeEach
    void setUp()
    {
        tokenConfig = tokenConfig( SECRET );
    }

    private static TokenConfig tokenConfig( String secret )
    {
        TokenConfig config = new TokenConfig();
        ReflectionTestUtils.setField( config, "secretKey", secret );
        return config;
    }

    @Test
    void tokenGeradoEValidoEContemIdEEmail()
    {
        String token = tokenConfig.getSecretKey( TestData.user( 7L, "ana@lab.local", Role.ADMIN ) );

        Optional<JWTUserData> data = tokenConfig.validateToken( token );

        assertThat( data ).isPresent();
        assertThat( data.get().getUserId() ).isEqualTo( 7L );
        assertThat( data.get().getEmail() ).isEqualTo( "ana@lab.local" );
    }

    @Test
    void tokenNaoCarregaARole()
    {
        // A role e sempre lida do banco pelo SecurityFilter
        String token = tokenConfig.getSecretKey( TestData.user( 7L, "ana@lab.local", Role.ADMIN ) );

        assertThat( tokenConfig.validateToken( token ).get().getRole() ).isNull();
    }

    @Test
    void tokenExpiraEm24Horas()
    {
        String token = tokenConfig.getSecretKey( TestData.user( 7L, "ana@lab.local", Role.USER ) );

        Instant expiresAt = JWT.decode( token ).getExpiresAtAsInstant();
        assertThat( expiresAt ).isCloseTo( Instant.now().plus( 24, ChronoUnit.HOURS ), within( 1, ChronoUnit.MINUTES ) );
    }

    @Test
    void tokenAssinadoComOutraChaveEInvalido()
    {
        String token = tokenConfig( "outra-chave" ).getSecretKey( TestData.user( 7L, "ana@lab.local", Role.USER ) );

        assertThat( tokenConfig.validateToken( token ) ).isEmpty();
    }

    @Test
    void tokenAdulteradoEInvalido()
    {
        String token = tokenConfig.getSecretKey( TestData.user( 7L, "ana@lab.local", Role.USER ) );
        String[] parts = token.split( "\\." );
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString( "{\"sub\":\"admin@lab.local\",\"userId\":1}".getBytes() );

        assertThat( tokenConfig.validateToken( parts[0] + "." + forgedPayload + "." + parts[2] ) ).isEmpty();
    }

    @Test
    void tokenExpiradoEInvalido()
    {
        String expired = JWT.create()
                .withSubject( "ana@lab.local" )
                .withClaim( "userId", 7L )
                .withExpiresAt( Instant.now().minusSeconds( 60 ) )
                .sign( Algorithm.HMAC256( SECRET ) );

        assertThat( tokenConfig.validateToken( expired ) ).isEmpty();
    }

    @ParameterizedTest
    @ValueSource( strings = { "", "lixo", "a.b.c", "eyJhbGciOiJIUzI1NiJ9.e30." } )
    void textoQueNaoEJwtEInvalido( String token )
    {
        assertThat( tokenConfig.validateToken( token ) ).isEmpty();
    }
}

package com.homelab.homepage.common.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.homelab.homepage.domain.user.model.JWTUserData;
import com.homelab.homepage.domain.user.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class TokenConfig
{
    @Value( "${security.secret-key}" )
    private String secretKey;

    /**
     * getSecretKey
     * @param user
     * @return
     */
    public String getSecretKey( User user )
    {
        Algorithm algorithm = Algorithm.HMAC256( secretKey );

        return JWT.create()
                .withClaim( "userId", user.getId() )
                .withSubject( user.getEmail() )
                .withExpiresAt( Instant.now().plusSeconds( 86400 ) )
                .withIssuedAt( Instant.now() )
                .sign( algorithm );
    }

    /**
     * validateToken
     * @param token
     * @return
     */
    public Optional<JWTUserData> validateToken( String token )
    {
        try
        {
            Algorithm algorithm = Algorithm.HMAC256( secretKey );

            DecodedJWT decodedJWT = JWT.require( algorithm )
                    .build().verify( token );

            return Optional.of( JWTUserData.builder()
                    .userId( decodedJWT.getClaim( "userId" ).asLong() )
                    .email( decodedJWT.getSubject() )
                    .build() );
        }
        catch ( JWTVerificationException exception )
        {
            return Optional.empty();
        }
    }
}
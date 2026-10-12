package com.homelab.homepage.common.config;

import com.homelab.homepage.domain.user.model.JWTUserData;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class SecurityFilter extends OncePerRequestFilter
{
    @Autowired
    private TokenConfig tokenConfig;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal( HttpServletRequest request, HttpServletResponse response, FilterChain filterChain ) throws ServletException, IOException
    {
        String authorizedHeader = request.getHeader( "Authorization" );

        if ( Strings.isNotEmpty( authorizedHeader ) && authorizedHeader.startsWith( "Bearer " ) )
        {
            String token = authorizedHeader.substring( "Bearer ".length() );

            Optional<JWTUserData> optTokenData = tokenConfig.validateToken( token );

            if ( optTokenData.isPresent() )
            {
                // A role vem sempre do banco (e nao do token): se o admin rebaixar ou remover
                // um usuario, o efeito e imediato, sem esperar o token expirar.
                Optional<UserDetails> optUserDetails = userRepository.findUserByEmail( optTokenData.get().getEmail() );

                if ( optUserDetails.isPresent() )
                {
                    User user = (User) optUserDetails.get();

                    JWTUserData principal = JWTUserData.builder()
                            .userId( user.getId() )
                            .email( user.getEmail() )
                            .role( user.getRole() )
                            .build();

                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken( principal, null, user.getAuthorities() );

                    SecurityContextHolder.getContext().setAuthentication( authenticationToken );
                }
            }
        }

        filterChain.doFilter( request, response );
    }
}

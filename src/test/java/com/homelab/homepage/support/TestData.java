package com.homelab.homepage.support;

import org.springframework.test.util.ReflectionTestUtils;

import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.model.Category;
import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;

/** Fabrica de entidades para os testes. Os ids sao gerados pelo banco, entao sao definidos via reflexao. */
public final class TestData
{
    private TestData()
    {
    }

    public static Category category( Long id, String label )
    {
        Category category = new Category( label, 0 );
        ReflectionTestUtils.setField( category, "id", id );
        return category;
    }

    public static Card card( Long id, Category category, String name, String url )
    {
        Card card = new Card( category, name, url, null, null, 0 );
        ReflectionTestUtils.setField( card, "id", id );
        return card;
    }

    public static Card card( Long id, String name, String url )
    {
        return card( id, null, name, url );
    }

    public static User user( Long id, String email, Role role )
    {
        User user = new User();
        user.setId( id );
        user.setEmail( email );
        user.setUsername( email.substring( 0, email.indexOf( '@' ) ) );
        user.setPassword( "hash" );
        user.setRole( role );
        return user;
    }
}

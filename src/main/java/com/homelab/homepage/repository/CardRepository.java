package com.homelab.homepage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.homelab.homepage.model.Card;

public interface CardRepository extends JpaRepository<Card, Long>
{

    @Query( "select coalesce(max(c.position), -1) + 1 from Card c where c.category.id = :categoryId" )
    int nextPosition( @Param( "categoryId" ) Long categoryId );
}

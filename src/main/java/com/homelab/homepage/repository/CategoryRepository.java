package com.homelab.homepage.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.homelab.homepage.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long>
{

    @Query( "select distinct c from Category c left join fetch c.cards order by c.position, c.id" )
    List<Category> findAllWithCards();

    @Query( "select coalesce(max(c.position), -1) + 1 from Category c" )
    int nextPosition();
}

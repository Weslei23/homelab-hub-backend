package com.homelab.homepage.domain.catalog.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table( name = "catalog_category" )
public class Category
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false, length = 100 )
    private String label;

    @Column( nullable = false )
    private int position;

    @OneToMany( mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true )
    @OrderBy( "position ASC, id ASC" )
    private List<Card> cards = new ArrayList<>();

    protected Category()
    {
    }

    public Category( String label, int position )
    {
        this.label = label;
        this.position = position;
    }

    public Long getId()
    {
        return id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel( String label )
    {
        this.label = label;
    }

    public int getPosition()
    {
        return position;
    }

    public List<Card> getCards()
    {
        return cards;
    }
}

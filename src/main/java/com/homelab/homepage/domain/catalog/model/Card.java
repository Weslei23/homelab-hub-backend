package com.homelab.homepage.domain.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table( name = "catalog_card" )
public class Card
{

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @ManyToOne( fetch = FetchType.LAZY, optional = false )
    @JoinColumn( name = "category_id" )
    private Category category;

    @Column( nullable = false, length = 100 )
    private String name;

    @Column( nullable = false, length = 2048 )
    private String url;

    @Column( length = 255 )
    private String description;

    /** Chave de um icone embutido no frontend (ex.: "proxmox") ou uma URL http(s). */
    @Column( length = 2048 )
    private String icon;

    @Column( nullable = false )
    private int position;

    protected Card()
    {
    }

    public Card( Category category, String name, String url, String description, String icon, int position )
    {
        this.category = category;
        this.name = name;
        this.url = url;
        this.description = description;
        this.icon = icon;
        this.position = position;
    }

    public void updateData( String name, String url, String description, String icon )
    {
        this.name = name;
        this.url = url;
        this.description = description;
        this.icon = icon;
    }

    public Long getId()
    {
        return id;
    }

    public Category getCategory()
    {
        return category;
    }

    public String getName()
    {
        return name;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl( String url )
    {
        this.url = url;
    }

    public String getDescription()
    {
        return description;
    }

    public String getIcon()
    {
        return icon;
    }

    public int getPosition()
    {
        return position;
    }
}

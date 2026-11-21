package com.homelab.homepage.domain.music.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Playlist do Spotify acompanhada pelo dashboard: faixas novas sao enviadas ao Downtify automaticamente. */
@Entity
@Table( name = "music_playlist" )
public class MusicPlaylist
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false, length = 100 )
    private String name;

    /** Link canonico (https://open.spotify.com/playlist/{id}). */
    @Column( nullable = false, unique = true, length = 255 )
    private String url;

    @Column( nullable = false )
    private boolean enabled = true;

    /** Faixas ja enviadas ao Downtify; so as que nao estao aqui sao baixadas na proxima sincronizacao. */
    @ElementCollection( fetch = FetchType.EAGER )
    @CollectionTable( name = "music_playlist_song", joinColumns = @JoinColumn( name = "playlist_id" ) )
    @Column( name = "song_key", nullable = false, length = 255 )
    private Set<String> knownSongs = new HashSet<>();

    private Instant lastSyncAt;

    @Column( length = 255 )
    private String lastSyncMessage;

    private Boolean lastSyncOk;

    @Column( nullable = false )
    private Instant createdAt = Instant.now();

    protected MusicPlaylist()
    {
    }

    public MusicPlaylist( String name, String url )
    {
        this.name = name;
        this.url = url;
    }

    public void recordSync( boolean ok, String message )
    {
        this.lastSyncAt = Instant.now();
        this.lastSyncOk = ok;
        this.lastSyncMessage = message == null || message.length() <= 255 ? message : message.substring( 0, 255 );
    }

    public Long getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public void setName( String name )
    {
        this.name = name;
    }

    public String getUrl()
    {
        return url;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public Set<String> getKnownSongs()
    {
        return knownSongs;
    }

    public Instant getLastSyncAt()
    {
        return lastSyncAt;
    }

    public String getLastSyncMessage()
    {
        return lastSyncMessage;
    }

    public Boolean getLastSyncOk()
    {
        return lastSyncOk;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }
}

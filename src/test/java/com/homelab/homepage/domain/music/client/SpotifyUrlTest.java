package com.homelab.homepage.domain.music.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.homelab.homepage.common.exceptions.BadRequestException;
import com.homelab.homepage.domain.music.client.SpotifyUrl.Type;

class SpotifyUrlTest
{
    private static final String ID = "37i9dQZF1DXcBWIGoYBM5M";

    @ParameterizedTest
    @CsvSource( {
            "https://open.spotify.com/playlist/" + ID + ", PLAYLIST",
            "https://open.spotify.com/track/" + ID + ", TRACK",
            "https://open.spotify.com/album/" + ID + ", ALBUM",
            "http://open.spotify.com/playlist/" + ID + ", PLAYLIST",
            "HTTPS://OPEN.SPOTIFY.COM/playlist/" + ID + ", PLAYLIST",
            "https://open.spotify.com/intl-pt/track/" + ID + ", TRACK",
            "https://open.spotify.com/intl-pt-br/album/" + ID + ", ALBUM",
            "https://open.spotify.com/playlist/" + ID + "?si=abc123&pi=x, PLAYLIST",
            "https://open.spotify.com/track/" + ID + "/, TRACK",
            "spotify:playlist:" + ID + ", PLAYLIST",
            "spotify:track:" + ID + ", TRACK",
            "'  https://open.spotify.com/album/" + ID + "  ', ALBUM" } )
    void aceitaLinksDoSpotify( String raw, Type type )
    {
        SpotifyUrl url = SpotifyUrl.parse( raw );

        assertThat( url.type() ).isEqualTo( type );
        assertThat( url.id() ).isEqualTo( ID );
    }

    @Test
    void normalizaRemovendoIdiomaERastreio()
    {
        assertThat( SpotifyUrl.parse( "https://open.spotify.com/intl-pt/playlist/" + ID + "?si=abc" ).url() )
                .isEqualTo( "https://open.spotify.com/playlist/" + ID );
        assertThat( SpotifyUrl.parse( "spotify:track:" + ID ).url() )
                .isEqualTo( "https://open.spotify.com/track/" + ID );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource( strings = {
            "   ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://open.spotify.com/artist/" + ID,
            "https://open.spotify.com/episode/" + ID,
            "https://open.spotify.com/playlist/curto",
            "https://open.spotify.com.evil.com/playlist/" + ID,
            "https://evil.com/?r=https://open.spotify.com/playlist/" + ID,
            "open.spotify.com/playlist/" + ID,
            "spotify:artist:" + ID } )
    void recusaOQueNaoEFaixaAlbumOuPlaylist( String raw )
    {
        assertThatThrownBy( () -> SpotifyUrl.parse( raw ) )
                .isInstanceOf( BadRequestException.class )
                .hasMessageContaining( "link do Spotify" );
    }

    @Test
    void rotulosEmPortugues()
    {
        assertThat( Type.TRACK.label() ).isEqualTo( "faixa" );
        assertThat( Type.ALBUM.label() ).isEqualTo( "album" );
        assertThat( Type.PLAYLIST.label() ).isEqualTo( "playlist" );
    }
}

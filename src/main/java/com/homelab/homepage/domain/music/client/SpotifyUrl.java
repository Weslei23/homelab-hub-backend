package com.homelab.homepage.domain.music.client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.homelab.homepage.common.exceptions.BadRequestException;

/**
 * Link do Spotify ja validado e normalizado.
 * Aceita https://open.spotify.com/{tipo}/{id} (com ou sem /intl-xx/ e ?si=...) e spotify:{tipo}:{id}.
 */
public record SpotifyUrl( Type type, String id )
{
    public enum Type
    {
        TRACK( "faixa" ), ALBUM( "album" ), PLAYLIST( "playlist" );

        private final String label;

        Type( String label )
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    private static final Pattern WEB = Pattern.compile(
            "^https?://open\\.spotify\\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(track|album|playlist)/([A-Za-z0-9]{22})(?:[/?#].*)?$",
            Pattern.CASE_INSENSITIVE );

    private static final Pattern URI = Pattern.compile( "^spotify:(track|album|playlist):([A-Za-z0-9]{22})$",
            Pattern.CASE_INSENSITIVE );

    public static SpotifyUrl parse( String raw )
    {
        String value = raw == null ? "" : raw.trim();
        for ( Pattern pattern : new Pattern[] { WEB, URI } )
        {
            Matcher matcher = pattern.matcher( value );
            if ( matcher.matches() )
            {
                Type type = Type.valueOf( matcher.group( 1 ).toUpperCase( Locale.ROOT ) );
                return new SpotifyUrl( type, matcher.group( 2 ) );
            }
        }
        throw new BadRequestException( "Use um link do Spotify de faixa, album ou playlist (https://open.spotify.com/...)" );
    }

    /** Forma canonica, sem parametros de rastreio (?si=) nem prefixo de idioma. */
    public String url()
    {
        return "https://open.spotify.com/" + type.name().toLowerCase( Locale.ROOT ) + "/" + id;
    }
}

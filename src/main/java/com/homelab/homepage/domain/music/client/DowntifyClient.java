package com.homelab.homepage.domain.music.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cliente HTTP do Downtify (https://github.com/henriquesebastiao/downtify, API 3.x).
 * O token (DOWNTIFY_TOKEN) fica so no backend: o navegador nunca fala direto com o Downtify.
 */
@Component
public class DowntifyClient
{
    private final String baseUrl;
    private final String token;
    private final Duration timeout;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public DowntifyClient(
            @Value( "${app.downtify.url:}" ) String baseUrl,
            @Value( "${app.downtify.token:}" ) String token,
            @Value( "${app.downtify.timeout:60s}" ) Duration timeout,
            JsonMapper jsonMapper )
    {
        String url = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = url.endsWith( "/" ) ? url.substring( 0, url.length() - 1 ) : url;
        this.token = token == null ? "" : token.trim();
        this.timeout = timeout;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout( Duration.ofSeconds( 5 ) )
                .build();
    }

    public boolean isConfigured()
    {
        return !baseUrl.isEmpty();
    }

    /** Versao do Downtify, ou vazio se ele nao responder. */
    public Optional<String> version()
    {
        if ( !isConfigured() )
        {
            return Optional.empty();
        }
        try
        {
            JsonNode health = send( get( "/api/health" ) );
            String version = text( health, "version" );
            return Optional.of( version == null ? "desconhecida" : version );
        }
        catch ( DowntifyException e )
        {
            return Optional.empty();
        }
    }

    /** Busca as faixas de um link do Spotify. Faixa devolve 1 item; album e playlist, varios. */
    public List<JsonNode> resolve( String spotifyUrl )
    {
        JsonNode result = send( get( "/api/song/url?url=" + URLEncoder.encode( spotifyUrl, StandardCharsets.UTF_8 ) ) );
        List<JsonNode> songs = new ArrayList<>();
        if ( result != null && result.isArray() )
        {
            result.forEach( songs::add );
        }
        else if ( result != null && result.isObject() )
        {
            songs.add( result );
        }
        return songs;
    }

    /** Coloca as faixas na fila do Downtify. Retorna na hora; o download acontece em segundo plano. */
    public void downloadBatch( List<JsonNode> songs, String playlistUrl )
    {
        ObjectNode body = jsonMapper.createObjectNode();
        ArrayNode array = body.putArray( "songs" );
        songs.forEach( array::add );
        if ( playlistUrl != null )
        {
            // Playlist: o Downtify agrupa as faixas e gera o arquivo .m3u (o Navidrome importa)
            body.put( "playlist_url", playlistUrl );
            body.put( "generate_m3u", true );
        }
        send( post( "/api/download/batch", body ) );
    }

    public List<JsonNode> queue()
    {
        JsonNode result = send( get( "/api/queue" ) );
        List<JsonNode> jobs = new ArrayList<>();
        if ( result != null && result.isArray() )
        {
            result.forEach( jobs::add );
        }
        return jobs;
    }

    public void clearCompleted()
    {
        send( request( "/api/queue/completed" ).DELETE() );
    }

    // ---- utilitarios para ler os objetos "song" do Downtify ----

    /** Texto de um campo, ou null se ausente. */
    public static String text( JsonNode node, String field )
    {
        JsonNode value = node == null ? null : node.get( field );
        return value == null || value.isNull() ? null : value.asString();
    }

    /** Artistas como texto: aceita lista ("artists") ou campo unico ("artist"). */
    public static String artists( JsonNode song )
    {
        JsonNode list = song == null ? null : song.get( "artists" );
        if ( list != null && list.isArray() && !list.isEmpty() )
        {
            List<String> names = new ArrayList<>();
            list.forEach( a -> names.add( a.isObject() ? text( a, "name" ) : a.asString() ) );
            return String.join( ", ", names );
        }
        return text( song, "artist" );
    }

    /** Identificador estavel da faixa, usado para saber o que ja foi baixado de uma playlist. */
    public static String songKey( JsonNode song )
    {
        for ( String field : List.of( "song_id", "id", "url" ) )
        {
            String value = text( song, field );
            if ( value != null && !value.isBlank() )
            {
                return value;
            }
        }
        return ( text( song, "name" ) + " - " + artists( song ) ).toLowerCase();
    }

    // ---- HTTP ----

    private HttpRequest.Builder request( String path )
    {
        if ( !isConfigured() )
        {
            throw new DowntifyException( "Downtify nao configurado: defina DOWNTIFY_URL no backend" );
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder( URI.create( baseUrl + path ) )
                .timeout( timeout )
                .header( "Accept", "application/json" );
        if ( !token.isEmpty() )
        {
            builder.header( "Authorization", "Bearer " + token );
        }
        return builder;
    }

    private HttpRequest.Builder get( String path )
    {
        return request( path ).GET();
    }

    private HttpRequest.Builder post( String path, JsonNode body )
    {
        return request( path )
                .header( "Content-Type", "application/json" )
                .POST( HttpRequest.BodyPublishers.ofString( jsonMapper.writeValueAsString( body ) ) );
    }

    private JsonNode send( HttpRequest.Builder builder )
    {
        HttpResponse<String> response;
        try
        {
            response = httpClient.send( builder.build(), HttpResponse.BodyHandlers.ofString() );
        }
        catch ( IOException e )
        {
            throw new DowntifyException( "Downtify indisponivel em " + baseUrl, e );
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new DowntifyException( "Requisicao ao Downtify interrompida", e );
        }

        int status = response.statusCode();
        if ( status == 401 || status == 403 )
        {
            throw new DowntifyException( "O Downtify recusou o acesso: confira o DOWNTIFY_TOKEN" );
        }
        if ( status == 404 )
        {
            throw new DowntifyException( "O Downtify nao encontrou nada para esse link" );
        }
        if ( status / 100 != 2 )
        {
            throw new DowntifyException( "O Downtify respondeu HTTP " + status );
        }
        String body = response.body();
        if ( body == null || body.isBlank() )
        {
            return null;
        }
        try
        {
            return jsonMapper.readTree( body );
        }
        catch ( JacksonException e )
        {
            throw new DowntifyException( "Resposta inesperada do Downtify", e );
        }
    }
}

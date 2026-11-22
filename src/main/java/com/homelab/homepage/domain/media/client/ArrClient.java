package com.homelab.homepage.domain.media.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente da API v3 do Sonarr (series) e do Radarr (filmes). As duas APIs sao quase iguais;
 * o que muda (caminho do recurso, busca por id) fica em {@link Kind}.
 * A API key fica so no backend: o navegador nunca fala direto com eles.
 */
public class ArrClient
{
    public enum Kind
    {
        SONARR( "Sonarr", "SONARR_API_KEY", "series" ),
        RADARR( "Radarr", "RADARR_API_KEY", "movie" );

        private final String label;
        private final String keyVariable;
        private final String resource;

        Kind( String label, String keyVariable, String resource )
        {
            this.label = label;
            this.keyVariable = keyVariable;
            this.resource = resource;
        }

        public String label()
        {
            return label;
        }
    }

    private final Kind kind;
    private final ServiceHttp http;

    public ArrClient( Kind kind, String baseUrl, String apiKey, Duration timeout, JsonMapper jsonMapper )
    {
        this.kind = kind;
        this.http = new ServiceHttp( kind.label, kind.keyVariable, baseUrl, "X-Api-Key",
                apiKey == null ? "" : apiKey.trim(), timeout, jsonMapper );
    }

    public Kind kind()
    {
        return kind;
    }

    public boolean isConfigured()
    {
        return http.isConfigured();
    }

    public String baseUrl()
    {
        return http.baseUrl();
    }

    /** Versao instalada; tambem confirma que a API key funciona. */
    public String version()
    {
        String version = text( http.get( "/api/v3/system/status" ), "version" );
        return version == null ? "desconhecida" : version;
    }

    /** Downloads em andamento (ja com a serie/episodio ou o filme de cada item). */
    public List<JsonNode> queue()
    {
        JsonNode page = http.get( "/api/v3/queue?page=1&pageSize=50&includeSeries=true&includeEpisode=true&includeMovie=true" );
        return list( page == null ? null : page.get( "records" ) );
    }

    /** Episodios (Sonarr) ou lancamentos de filmes (Radarr) entre as datas, inclusive. */
    public List<JsonNode> calendar( LocalDate start, LocalDate end )
    {
        return list( http.get( "/api/v3/calendar?start=" + start + "&end=" + end + "&includeSeries=true&unmonitored=false" ) );
    }

    /** Busca por nome no TVDB (Sonarr) ou no TMDB (Radarr). Itens com "id" &gt; 0 ja estao na biblioteca. */
    public List<JsonNode> lookup( String term )
    {
        return list( http.get( "/api/v3/" + kind.resource + "/lookup?term=" + encode( term ) ) );
    }

    /** Um titulo pelo id externo: tvdbId (Sonarr) ou tmdbId (Radarr). */
    public Optional<JsonNode> lookupById( long externalId )
    {
        if ( kind == Kind.SONARR )
        {
            return lookup( "tvdb:" + externalId ).stream().findFirst();
        }
        JsonNode movie = http.get( "/api/v3/movie/lookup/tmdb?tmdbId=" + externalId );
        return movie == null || !movie.isObject() ? Optional.empty() : Optional.of( movie );
    }

    public List<JsonNode> qualityProfiles()
    {
        return list( http.get( "/api/v3/qualityprofile" ) );
    }

    public List<JsonNode> rootFolders()
    {
        return list( http.get( "/api/v3/rootfolder" ) );
    }

    /** Adiciona a serie ou o filme a biblioteca; com as addOptions certas, ja comeca a busca. */
    public JsonNode add( JsonNode item )
    {
        return http.post( "/api/v3/" + kind.resource, item );
    }

    // ---- utilitarios ----

    static List<JsonNode> list( JsonNode node )
    {
        List<JsonNode> items = new ArrayList<>();
        if ( node != null && node.isArray() )
        {
            node.forEach( items::add );
        }
        return items;
    }

    public static String text( JsonNode node, String field )
    {
        JsonNode value = node == null ? null : node.get( field );
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String encode( String value )
    {
        // %20 em vez de "+" para espaco: vale em qualquer servidor
        return URLEncoder.encode( value, StandardCharsets.UTF_8 ).replace( "+", "%20" );
    }
}

package com.homelab.homepage.domain.media.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP com JSON para um servico do homelab (Sonarr, Radarr ou Jellyfin).
 * Traduz as falhas em {@link MediaException} com mensagens que fazem sentido na tela.
 */
final class ServiceHttp
{
    private final String name;
    private final String keyVariable;
    private final String baseUrl;
    private final String authHeader;
    private final String authValue;
    private final Duration timeout;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    /**
     * @param name        nome do servico nas mensagens (ex.: "Sonarr")
     * @param keyVariable variavel de ambiente da chave, citada quando o acesso e recusado
     * @param authHeader  cabecalho que leva a chave
     * @param authValue   valor do cabecalho (vazio = sem autenticacao)
     */
    ServiceHttp( String name, String keyVariable, String baseUrl, String authHeader, String authValue, Duration timeout,
            JsonMapper jsonMapper )
    {
        String url = baseUrl == null ? "" : baseUrl.trim();
        this.name = name;
        this.keyVariable = keyVariable;
        this.baseUrl = url.endsWith( "/" ) ? url.substring( 0, url.length() - 1 ) : url;
        this.authHeader = authHeader;
        this.authValue = authValue == null ? "" : authValue;
        this.timeout = timeout;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout( Duration.ofSeconds( 5 ) )
                .build();
    }

    boolean isConfigured()
    {
        return !baseUrl.isEmpty();
    }

    String baseUrl()
    {
        return baseUrl;
    }

    String name()
    {
        return name;
    }

    JsonNode get( String pathAndQuery )
    {
        return send( request( pathAndQuery ).GET() );
    }

    JsonNode post( String path, JsonNode body )
    {
        return send( request( path )
                .header( "Content-Type", "application/json" )
                .POST( HttpRequest.BodyPublishers.ofString( jsonMapper.writeValueAsString( body ) ) ) );
    }

    private HttpRequest.Builder request( String path )
    {
        if ( !isConfigured() )
        {
            throw new MediaException( name + " nao configurado no backend" );
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder( URI.create( baseUrl + path ) )
                .timeout( timeout )
                .header( "Accept", "application/json" );
        if ( !authValue.isBlank() )
        {
            builder.header( authHeader, authValue );
        }
        return builder;
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
            throw new MediaException( name + " indisponivel em " + baseUrl, e );
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new MediaException( "Requisicao ao " + name + " interrompida", e );
        }

        int status = response.statusCode();
        String body = response.body();
        if ( status == 401 || status == 403 )
        {
            throw new MediaException( "O " + name + " recusou o acesso: confira o " + keyVariable );
        }
        if ( status == 400 || status == 409 )
        {
            throw new MediaException( name + ": " + validationMessage( body ) );
        }
        if ( status == 404 )
        {
            throw new MediaException( "O " + name + " nao encontrou o que foi pedido" );
        }
        if ( status / 100 != 2 )
        {
            throw new MediaException( "O " + name + " respondeu HTTP " + status );
        }
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
            throw new MediaException( "Resposta inesperada do " + name, e );
        }
    }

    /** Erro de validacao do Sonarr/Radarr: lista de {errorMessage} ou objeto com "message". */
    private String validationMessage( String body )
    {
        try
        {
            JsonNode node = body == null || body.isBlank() ? null : jsonMapper.readTree( body );
            if ( node != null && node.isArray() && !node.isEmpty() && node.get( 0 ).hasNonNull( "errorMessage" ) )
            {
                return node.get( 0 ).get( "errorMessage" ).asString();
            }
            if ( node != null && node.hasNonNull( "message" ) )
            {
                return node.get( "message" ).asString();
            }
        }
        catch ( JacksonException e )
        {
            // corpo que nao e JSON: cai na mensagem generica
        }
        return "pedido recusado";
    }
}

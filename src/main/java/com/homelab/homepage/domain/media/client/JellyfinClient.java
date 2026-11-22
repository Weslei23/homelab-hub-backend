package com.homelab.homepage.domain.media.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente da API do Jellyfin (10.9+). A API key e criada em Painel &gt; Chaves de API. */
@Component
public class JellyfinClient
{
    private final ServiceHttp http;
    /** Usuario administrador, usado para listar os itens recentes (a rota exige um usuario). */
    private volatile String adminUserId;

    public JellyfinClient(
            @Value( "${app.media.jellyfin.url:}" ) String baseUrl,
            @Value( "${app.media.jellyfin.api-key:}" ) String apiKey,
            @Value( "${app.media.timeout:15s}" ) Duration timeout,
            JsonMapper jsonMapper )
    {
        String key = apiKey == null ? "" : apiKey.trim();
        this.http = new ServiceHttp( "Jellyfin", "JELLYFIN_API_KEY", baseUrl, "Authorization",
                key.isEmpty() ? "" : "MediaBrowser Token=\"" + key + "\"", timeout, jsonMapper );
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
        String version = ArrClient.text( http.get( "/System/Info" ), "Version" );
        return version == null ? "desconhecida" : version;
    }

    /** Ultimos itens adicionados, com episodios da mesma serie agrupados. */
    public List<JsonNode> latest( int limit )
    {
        return ArrClient.list( http.get( "/Items/Latest?userId=" + adminUserId() + "&limit=" + limit
                + "&groupItems=true&includeItemTypes=Movie&includeItemTypes=Episode&fields=DateCreated,ProductionYear" ) );
    }

    /** Sessoes ativas (quem esta com o app aberto e, se estiver assistindo, o que). */
    public List<JsonNode> sessions()
    {
        return ArrClient.list( http.get( "/Sessions?activeWithinSeconds=960" ) );
    }

    /** Endereco da capa: o Jellyfin serve imagens sem autenticacao. */
    public String imageUrl( String itemId )
    {
        return itemId == null ? null : http.baseUrl() + "/Items/" + itemId + "/Images/Primary?fillHeight=300&quality=90";
    }

    /** Pagina do item no Jellyfin. */
    public String itemUrl( String itemId )
    {
        return itemId == null ? null : http.baseUrl() + "/web/#/details?id=" + itemId;
    }

    private String adminUserId()
    {
        String cached = adminUserId;
        if ( cached != null )
        {
            return cached;
        }
        List<JsonNode> users = ArrClient.list( http.get( "/Users" ) );
        JsonNode chosen = users.stream()
                .filter( u -> u.path( "Policy" ).path( "IsAdministrator" ).asBoolean( false ) )
                .findFirst()
                .orElse( users.isEmpty() ? null : users.getFirst() );
        String id = ArrClient.text( chosen, "Id" );
        if ( id == null )
        {
            throw new MediaException( "Nenhum usuario encontrado no Jellyfin" );
        }
        adminUserId = id;
        return id;
    }
}

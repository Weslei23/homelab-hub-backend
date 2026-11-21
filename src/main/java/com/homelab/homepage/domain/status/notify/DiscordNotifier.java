package com.homelab.homepage.domain.status.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Envia avisos de queda/retorno de servicos para um webhook do Discord.
 * Desativado quando app.notify.discord.webhook-url esta vazio.
 */
@Component
public class DiscordNotifier
{
    private static final Logger log = LoggerFactory.getLogger( DiscordNotifier.class );

    private static final int RED = 0xEF4444;
    private static final int GREEN = 0x22C55E;
    private static final int BLUE = 0x2563EB;
    private static final int PURPLE = 0x8B5CF6;
    /** Quantas faixas aparecem na mensagem; o resto vira "+ N outras". */
    static final int MAX_LISTED_TRACKS = 10;

    private final String webhookUrl;
    private final String username;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout( Duration.ofSeconds( 5 ) )
            .build();

    public DiscordNotifier(
            @Value( "${app.notify.discord.webhook-url:}" ) String webhookUrl,
            @Value( "${app.notify.discord.username:Homelab Hub}" ) String username,
            JsonMapper jsonMapper )
    {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.username = username;
        this.jsonMapper = jsonMapper;
        if ( isEnabled() )
        {
            log.info( "Avisos no Discord ativados" );
        }
    }

    public boolean isEnabled()
    {
        return !webhookUrl.isEmpty();
    }

    public void notifyDown( String serviceName, String url, Integer httpStatus )
    {
        String reason = httpStatus != null ? "Respondeu HTTP " + httpStatus : "Sem resposta (timeout ou conexao recusada)";
        send( embed( "🔴 " + serviceName + " está offline", url, RED,
                List.of( field( "Motivo", reason ) ) ) );
    }

    public void notifyUp( String serviceName, String url, long latencyMs, Duration downtime )
    {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add( field( "Latência", latencyMs + " ms" ) );
        if ( downtime != null )
        {
            fields.add( field( "Ficou fora do ar por", format( downtime ) ) );
        }
        send( embed( "🟢 " + serviceName + " voltou", url, GREEN, fields ) );
    }

    /** Faixas novas de uma playlist acompanhada foram enviadas ao Downtify. */
    public void notifyNewMusic( String playlistName, String playlistUrl, List<String> tracks )
    {
        StringBuilder list = new StringBuilder();
        tracks.stream().limit( MAX_LISTED_TRACKS ).forEach( track -> list.append( "• " ).append( track ).append( '\n' ) );
        if ( tracks.size() > MAX_LISTED_TRACKS )
        {
            list.append( "+ " ).append( tracks.size() - MAX_LISTED_TRACKS ).append( " outras" );
        }
        // O Discord recusa campos com mais de 1024 caracteres
        String value = list.toString().strip();
        if ( value.length() > 1024 )
        {
            value = value.substring( 0, 1021 ) + "...";
        }
        Map<String, Object> embed = embed( "🎵 " + tracks.size() + " música(s) nova(s) em " + playlistName, playlistUrl, PURPLE,
                List.of( field( "Faixas", value ) ) );
        send( embed );
    }

    /** Mensagem de teste; devolve true se o Discord aceitou. */
    public boolean sendTest()
    {
        if ( !isEnabled() )
        {
            return false;
        }
        Map<String, Object> payload = payload( embed( "✅ Teste do Homelab Hub", null, BLUE,
                List.of( field( "Status", "Os avisos de queda e retorno vão chegar neste canal." ) ) ) );
        return post( payload ).join();
    }

    private void send( Map<String, Object> embed )
    {
        if ( !isEnabled() )
        {
            return;
        }
        // Assincrono: nao segura o ciclo de verificacao
        post( payload( embed ) );
    }

    private CompletableFuture<Boolean> post( Map<String, Object> payload )
    {
        HttpRequest request = HttpRequest.newBuilder( URI.create( webhookUrl ) )
                .timeout( Duration.ofSeconds( 10 ) )
                .header( "Content-Type", "application/json" )
                .POST( HttpRequest.BodyPublishers.ofString( jsonMapper.writeValueAsString( payload ) ) )
                .build();

        return httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                .thenApply( response -> {
                    boolean ok = response.statusCode() / 100 == 2;
                    if ( !ok )
                    {
                        log.warn( "Discord recusou o aviso: HTTP {} {}", response.statusCode(), response.body() );
                    }
                    return ok;
                } )
                .exceptionally( e -> {
                    log.warn( "Falha ao enviar aviso para o Discord: {}", e.getMessage() );
                    return false;
                } );
    }

    private Map<String, Object> payload( Map<String, Object> embed )
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put( "username", username );
        payload.put( "embeds", List.of( embed ) );
        return payload;
    }

    private static Map<String, Object> embed( String title, String url, int color, List<Map<String, Object>> fields )
    {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put( "title", title );
        if ( url != null && url.startsWith( "http" ) )
        {
            embed.put( "url", url );
            embed.put( "description", url );
        }
        embed.put( "color", color );
        embed.put( "fields", fields );
        embed.put( "timestamp", Instant.now().toString() );
        return embed;
    }

    private static Map<String, Object> field( String name, String value )
    {
        return Map.of( "name", name, "value", value, "inline", true );
    }

    private static String format( Duration d )
    {
        long minutes = d.toMinutes();
        if ( minutes < 1 )
        {
            return d.toSeconds() + " s";
        }
        if ( minutes < 60 )
        {
            return minutes + " min";
        }
        return d.toHours() + " h " + d.toMinutesPart() + " min";
    }
}

package com.homelab.homepage.domain.status.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.repository.CardRepository;
import com.homelab.homepage.domain.status.model.ServiceStatus;
import com.homelab.homepage.domain.status.model.ServiceStatus.State;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;

/**
 * Verifica periodicamente se a URL de cada card responde e guarda o resultado em memoria.
 * Qualquer resposta HTTP abaixo de 500 (inclusive 401/403/404) conta como online:
 * significa que o servidor esta de pe, mesmo que exija login.
 */
@Service
public class StatusService
{
    private static final Logger log = LoggerFactory.getLogger( StatusService.class );

    private final CardRepository cardRepository;
    private final DiscordNotifier notifier;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final int failureThreshold;
    private final Map<Long, ServiceStatus> statuses = new ConcurrentHashMap<>();
    private final Map<Long, Tracker> trackers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** Estado usado para decidir quando avisar; separado do status exibido no card. */
    private static final class Tracker
    {
        int consecutiveFailures;
        /** Ultimo estado ja confirmado (e avisado). null = ainda nao sabemos. */
        State confirmed;
        Instant downSince;
    }

    public StatusService(
            CardRepository cardRepository,
            DiscordNotifier notifier,
            @Value( "${app.status.timeout:3s}" ) Duration timeout,
            @Value( "${app.status.insecure-tls:true}" ) boolean insecureTls,
            @Value( "${app.notify.failure-threshold:2}" ) int failureThreshold )
    {
        this.cardRepository = cardRepository;
        this.notifier = notifier;
        this.timeout = timeout;
        this.failureThreshold = Math.max( 1, failureThreshold );

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout( timeout )
                .followRedirects( HttpClient.Redirect.NEVER )
                .executor( executor );
        if ( insecureTls )
        {
            // Homelabs costumam usar certificados autoassinados (Proxmox, TrueNAS...)
            builder.sslContext( trustAllContext() );
        }
        this.httpClient = builder.build();
    }

    public Map<Long, ServiceStatus> getAll()
    {
        return Map.copyOf( statuses );
    }

    @Scheduled( initialDelayString = "${app.status.initial-delay:5s}", fixedDelayString = "${app.status.interval:30s}" )
    public void refreshAll()
    {
        List<Card> cards = cardRepository.findAll();
        Set<Long> currentIds = cards.stream().map( Card::getId ).collect( Collectors.toSet() );
        statuses.keySet().retainAll( currentIds );
        trackers.keySet().retainAll( currentIds );

        List<Future<?>> futures = new ArrayList<>();
        for ( Card card : cards )
        {
            Long id = card.getId();
            String name = card.getName();
            String url = card.getUrl();
            futures.add( executor.submit( () -> record( id, name, url, check( url ) ) ) );
        }
        for ( Future<?> future : futures )
        {
            try
            {
                future.get();
            }
            catch ( Exception e )
            {
                log.debug( "Falha ao verificar status", e );
            }
        }
    }

    /**
     * Verifica um card fora do ciclo (ex.: logo apos criar ou editar).
     * So atualiza o indicador: nao conta para os avisos, para que duas checagens
     * em sequencia rapida nao disparem um alarme.
     */
    public void checkSoon( Long cardId, String url )
    {
        executor.submit( () -> statuses.put( cardId, check( url ) ) );
    }

    public void evict( Long cardId )
    {
        statuses.remove( cardId );
        trackers.remove( cardId );
    }

    /** Guarda o resultado e avisa no Discord quando o estado confirmado muda. */
    private void record( Long cardId, String name, String url, ServiceStatus status )
    {
        statuses.put( cardId, status );

        if ( status.state() == State.UNKNOWN )
        {
            trackers.remove( cardId );
            return;
        }

        Tracker tracker = trackers.computeIfAbsent( cardId, id -> new Tracker() );
        synchronized ( tracker )
        {
            if ( status.state() == State.UP )
            {
                if ( tracker.confirmed == State.DOWN )
                {
                    Duration downtime = tracker.downSince == null ? null
                            : Duration.between( tracker.downSince, status.checkedAt() );
                    notifier.notifyUp( name, url, status.latencyMs(), downtime );
                }
                tracker.consecutiveFailures = 0;
                tracker.confirmed = State.UP;
                tracker.downSince = null;
                return;
            }

            // DOWN
            tracker.consecutiveFailures++;
            if ( tracker.consecutiveFailures == 1 )
            {
                tracker.downSince = status.checkedAt();
            }
            if ( tracker.consecutiveFailures >= failureThreshold && tracker.confirmed != State.DOWN )
            {
                notifier.notifyDown( name, url, status.httpStatus() );
                tracker.confirmed = State.DOWN;
            }
        }
    }

    private ServiceStatus check( String url )
    {
        URI uri = toHttpUri( url );
        if ( uri == null )
        {
            return ServiceStatus.unknown();
        }

        long start = System.nanoTime();
        try
        {
            int code = send( uri, "HEAD" );
            // Alguns servicos nao implementam HEAD: tenta GET antes de concluir
            if ( code == 405 || code == 501 )
            {
                start = System.nanoTime();
                code = send( uri, "GET" );
            }
            long latencyMs = Duration.ofNanos( System.nanoTime() - start ).toMillis();
            return code < 500 ? ServiceStatus.up( latencyMs, code ) : ServiceStatus.down( code );
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            return ServiceStatus.down( null );
        }
        catch ( Exception e )
        {
            return ServiceStatus.down( null );
        }
    }

    private int send( URI uri, String method ) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder( uri )
                .timeout( timeout )
                .method( method, HttpRequest.BodyPublishers.noBody() )
                .header( "User-Agent", "homelab-hub-status" )
                .build();
        return httpClient.send( request, HttpResponse.BodyHandlers.discarding() ).statusCode();
    }

    private static URI toHttpUri( String url )
    {
        if ( url == null || url.isBlank() || url.trim().equals( "#" ) )
        {
            return null;
        }
        try
        {
            URI uri = URI.create( url.trim() );
            String scheme = uri.getScheme();
            if ( scheme == null || !( scheme.equalsIgnoreCase( "http" ) || scheme.equalsIgnoreCase( "https" ) ) )
            {
                return null;
            }
            return uri.getHost() == null ? null : uri;
        }
        catch ( IllegalArgumentException e )
        {
            return null;
        }
    }

    private static SSLContext trustAllContext()
    {
        // X509ExtendedTrustManager vazio tambem desativa a verificacao de hostname do HttpClient
        TrustManager trustAll = new X509ExtendedTrustManager()
        {
            public void checkClientTrusted( X509Certificate[] chain, String authType, Socket socket ) { }
            public void checkServerTrusted( X509Certificate[] chain, String authType, Socket socket ) { }
            public void checkClientTrusted( X509Certificate[] chain, String authType, SSLEngine engine ) { }
            public void checkServerTrusted( X509Certificate[] chain, String authType, SSLEngine engine ) { }
            public void checkClientTrusted( X509Certificate[] chain, String authType ) { }
            public void checkServerTrusted( X509Certificate[] chain, String authType ) { }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        try
        {
            SSLContext context = SSLContext.getInstance( "TLS" );
            context.init( null, new TrustManager[] { trustAll }, new SecureRandom() );
            return context;
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Nao foi possivel criar o SSLContext", e );
        }
    }
}

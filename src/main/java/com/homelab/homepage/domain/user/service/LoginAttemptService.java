package com.homelab.homepage.domain.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.homelab.homepage.common.exceptions.TooManyRequestsException;

/**
 * Protecao contra tentativa e erro de senhas: depois de {@code maxFailures} erros dentro da janela,
 * o IP ou o e-mail fica bloqueado ate o erro mais antigo sair da janela.
 * Fica em memoria: reiniciar o backend zera os contadores (aceitavel para um homelab).
 */
@Component
public class LoginAttemptService
{
    /** Acima disso, entradas vencidas sao descartadas (evita crescer sem limite com e-mails aleatorios). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
            @Value( "${app.auth.login.max-failures:5}" ) int maxFailures,
            @Value( "${app.auth.login.window:15m}" ) Duration window )
    {
        this( maxFailures, window, Clock.systemUTC() );
    }

    LoginAttemptService( int maxFailures, Duration window, Clock clock )
    {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** Falha com 429 se o IP ou o e-mail estiver bloqueado. */
    public void checkAllowed( String ip, String email )
    {
        Instant now = clock.instant();
        for ( String key : keys( ip, email ) )
        {
            Deque<Instant> recent = failures.get( key );
            if ( recent == null )
            {
                continue;
            }
            synchronized ( recent )
            {
                prune( recent, now );
                if ( recent.size() >= maxFailures )
                {
                    Duration wait = Duration.between( now, recent.peekFirst().plus( window ) );
                    long minutes = Math.max( 1, ( wait.toSeconds() + 59 ) / 60 );
                    throw new TooManyRequestsException(
                            "Muitas tentativas de login. Tente novamente em " + minutes + " min", wait.toSeconds() );
                }
            }
        }
    }

    public void recordFailure( String ip, String email )
    {
        Instant now = clock.instant();
        if ( failures.size() > CLEANUP_THRESHOLD )
        {
            failures.entrySet().removeIf( e -> {
                synchronized ( e.getValue() )
                {
                    prune( e.getValue(), now );
                    return e.getValue().isEmpty();
                }
            } );
        }
        for ( String key : keys( ip, email ) )
        {
            Deque<Instant> recent = failures.computeIfAbsent( key, k -> new ArrayDeque<>() );
            synchronized ( recent )
            {
                prune( recent, now );
                recent.addLast( now );
            }
        }
    }

    /** Login certo zera o contador do e-mail e do IP. */
    public void recordSuccess( String ip, String email )
    {
        keys( ip, email ).forEach( failures::remove );
    }

    private void prune( Deque<Instant> recent, Instant now )
    {
        Instant limit = now.minus( window );
        while ( !recent.isEmpty() && !recent.peekFirst().isAfter( limit ) )
        {
            recent.removeFirst();
        }
    }

    private static List<String> keys( String ip, String email )
    {
        List<String> keys = new ArrayList<>( 2 );
        if ( ip != null && !ip.isBlank() )
        {
            keys.add( "ip:" + ip.trim() );
        }
        if ( email != null && !email.isBlank() )
        {
            keys.add( "email:" + email.trim().toLowerCase( Locale.ROOT ) );
        }
        return keys;
    }
}

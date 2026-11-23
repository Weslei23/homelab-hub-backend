package com.homelab.homepage.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.homelab.homepage.common.exceptions.TooManyRequestsException;

class LoginAttemptServiceTest
{
    /** Relogio que o teste avanca. */
    private static final class MutableClock extends Clock
    {
        private Instant now = Instant.parse( "2026-11-22T12:00:00Z" );

        void advance( Duration duration )
        {
            now = now.plus( duration );
        }

        @Override
        public Instant instant()
        {
            return now;
        }

        @Override
        public java.time.ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone( java.time.ZoneId zone )
        {
            return this;
        }
    }

    private MutableClock clock;
    private LoginAttemptService attempts;

    @BeforeEach
    void setUp()
    {
        clock = new MutableClock();
        attempts = new LoginAttemptService( 3, Duration.ofMinutes( 15 ), clock );
    }

    private void fail( String ip, String email, int times )
    {
        for ( int i = 0; i < times; i++ )
        {
            attempts.recordFailure( ip, email );
        }
    }

    @Test
    void abaixoDoLimitePodeTentar()
    {
        fail( "1.1.1.1", "ana@lab.local", 2 );

        assertThatCode( () -> attempts.checkAllowed( "1.1.1.1", "ana@lab.local" ) ).doesNotThrowAnyException();
    }

    @Test
    void noLimiteBloqueiaComTempoDeEspera()
    {
        fail( "1.1.1.1", "ana@lab.local", 3 );
        clock.advance( Duration.ofMinutes( 5 ) );

        assertThatThrownBy( () -> attempts.checkAllowed( "1.1.1.1", "ana@lab.local" ) )
                .isInstanceOf( TooManyRequestsException.class )
                .hasMessage( "Muitas tentativas de login. Tente novamente em 10 min" )
                .extracting( e -> ( (TooManyRequestsException) e ).getRetryAfterSeconds() )
                .isEqualTo( 600L );
    }

    @Test
    void bloqueioPorEmailValeDeOutroIpEIgnoraMaiusculas()
    {
        fail( "1.1.1.1", "Ana@Lab.local", 3 );

        assertThatThrownBy( () -> attempts.checkAllowed( "9.9.9.9", " ana@lab.local " ) )
                .isInstanceOf( TooManyRequestsException.class );
    }

    @Test
    void bloqueioPorIpValeParaOutroEmail()
    {
        attempts.recordFailure( "1.1.1.1", "a@lab.local" );
        attempts.recordFailure( "1.1.1.1", "b@lab.local" );
        attempts.recordFailure( "1.1.1.1", "c@lab.local" );

        assertThatThrownBy( () -> attempts.checkAllowed( "1.1.1.1", "d@lab.local" ) )
                .isInstanceOf( TooManyRequestsException.class );
        assertThatCode( () -> attempts.checkAllowed( "2.2.2.2", "d@lab.local" ) ).doesNotThrowAnyException();
    }

    @Test
    void errosAntigosSaemDaJanela()
    {
        fail( "1.1.1.1", "ana@lab.local", 3 );
        clock.advance( Duration.ofMinutes( 15 ) );

        assertThatCode( () -> attempts.checkAllowed( "1.1.1.1", "ana@lab.local" ) ).doesNotThrowAnyException();
    }

    @Test
    void janelaDeslizante()
    {
        fail( "1.1.1.1", "ana@lab.local", 2 );
        clock.advance( Duration.ofMinutes( 10 ) );
        fail( "1.1.1.1", "ana@lab.local", 1 );

        assertThatThrownBy( () -> attempts.checkAllowed( "1.1.1.1", "ana@lab.local" ) )
                .hasMessage( "Muitas tentativas de login. Tente novamente em 5 min" );
        clock.advance( Duration.ofMinutes( 5 ) );
        // Os 2 primeiros erros sairam da janela; sobrou 1
        assertThatCode( () -> attempts.checkAllowed( "1.1.1.1", "ana@lab.local" ) ).doesNotThrowAnyException();
    }

    @Test
    void sucessoZeraOsContadores()
    {
        fail( "1.1.1.1", "ana@lab.local", 2 );
        attempts.recordSuccess( "1.1.1.1", "ana@lab.local" );
        fail( "1.1.1.1", "ana@lab.local", 2 );

        assertThatCode( () -> attempts.checkAllowed( "1.1.1.1", "ana@lab.local" ) ).doesNotThrowAnyException();
    }

    @Test
    void ultimoMinutoArredondaParaCima()
    {
        fail( "1.1.1.1", null, 3 );
        clock.advance( Duration.ofMinutes( 14 ).plusSeconds( 50 ) );

        assertThatThrownBy( () -> attempts.checkAllowed( "1.1.1.1", null ) )
                .hasMessage( "Muitas tentativas de login. Tente novamente em 1 min" );
    }

    @Test
    void semIpNemEmailNaoQuebra()
    {
        attempts.recordFailure( null, " " );

        assertThatCode( () -> attempts.checkAllowed( null, null ) ).doesNotThrowAnyException();
    }

    @Test
    void muitasChavesVencidasSaoDescartadas()
    {
        for ( int i = 0; i < 10_001; i++ )
        {
            attempts.recordFailure( null, "u" + i + "@lab.local" );
        }
        clock.advance( Duration.ofMinutes( 16 ) );
        attempts.recordFailure( null, "novo@lab.local" );

        assertThat( attempts ).extracting( "failures" ).asInstanceOf( org.assertj.core.api.InstanceOfAssertFactories.MAP )
                .hasSize( 1 );
    }
}

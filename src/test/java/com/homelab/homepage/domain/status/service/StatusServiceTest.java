package com.homelab.homepage.domain.status.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.homelab.homepage.domain.catalog.model.Card;
import com.homelab.homepage.domain.catalog.repository.CardRepository;
import com.homelab.homepage.domain.status.model.ServiceStatus;
import com.homelab.homepage.domain.status.model.ServiceStatus.State;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;
import com.homelab.homepage.support.FakeHttpServer;
import com.homelab.homepage.support.TestData;

class StatusServiceTest
{
    private static final Duration TIMEOUT = Duration.ofMillis( 400 );

    private FakeHttpServer server;
    private CardRepository cardRepository;
    private DiscordNotifier notifier;
    /** Codigo devolvido por /toggle; muda ao longo do teste para simular quedas. */
    private final AtomicInteger toggleCode = new AtomicInteger( 200 );

    @BeforeEach
    void setUp()
    {
        server = new FakeHttpServer()
                .respond( "/ok", 200 )
                .respond( "/auth", 401 )
                .respond( "/notfound", 404 )
                .respond( "/redirect", 302 )
                .respond( "/error", 503 )
                .handle( "/nohead", exchange -> FakeHttpServer.reply( exchange,
                        exchange.getRequestMethod().equals( "HEAD" ) ? 405 : 200 ) )
                .handle( "/nohead-down", exchange -> FakeHttpServer.reply( exchange,
                        exchange.getRequestMethod().equals( "HEAD" ) ? 501 : 500 ) )
                .handle( "/slow", exchange -> {
                    try
                    {
                        Thread.sleep( TIMEOUT.multipliedBy( 4 ).toMillis() );
                    }
                    catch ( InterruptedException ignored )
                    {
                        Thread.currentThread().interrupt();
                    }
                    FakeHttpServer.reply( exchange, 200 );
                } )
                .handle( "/toggle", exchange -> FakeHttpServer.reply( exchange, toggleCode.get() ) );
        cardRepository = mock( CardRepository.class );
        notifier = mock( DiscordNotifier.class );
    }

    @AfterEach
    void tearDown()
    {
        server.close();
    }

    private StatusService service( int failureThreshold )
    {
        return new StatusService( cardRepository, notifier, TIMEOUT, true, failureThreshold );
    }

    private StatusService service()
    {
        return service( 2 );
    }

    /** Executa um ciclo com um unico card e devolve o status dele. */
    private ServiceStatus checkOnce( String url )
    {
        when( cardRepository.findAll() ).thenReturn( List.of( TestData.card( 1L, "Servico", url ) ) );
        StatusService service = service();
        service.refreshAll();
        return service.getAll().get( 1L );
    }

    @Nested
    class Verificacao
    {
        @Test
        void onlineQuandoResponde200()
        {
            ServiceStatus status = checkOnce( server.url( "/ok" ) );

            assertThat( status.state() ).isEqualTo( State.UP );
            assertThat( status.httpStatus() ).isEqualTo( 200 );
            assertThat( status.latencyMs() ).isNotNull().isGreaterThanOrEqualTo( 0L );
        }

        @ParameterizedTest
        @ValueSource( strings = { "/auth", "/notfound", "/redirect" } )
        void respostasAbaixoDe500ContamComoOnline( String path )
        {
            assertThat( checkOnce( server.url( path ) ).state() ).isEqualTo( State.UP );
        }

        @Test
        void offlineQuandoResponde5xx()
        {
            ServiceStatus status = checkOnce( server.url( "/error" ) );

            assertThat( status.state() ).isEqualTo( State.DOWN );
            assertThat( status.httpStatus() ).isEqualTo( 503 );
            assertThat( status.latencyMs() ).isNull();
        }

        @Test
        void usaHeadPrimeiro()
        {
            checkOnce( server.url( "/ok" ) );

            assertThat( server.received( "/ok" ) ).extracting( FakeHttpServer.Received::method ).containsExactly( "HEAD" );
        }

        @Test
        void tentaGetQuandoHeadNaoESuportado()
        {
            ServiceStatus status = checkOnce( server.url( "/nohead" ) );

            assertThat( status.state() ).isEqualTo( State.UP );
            assertThat( status.httpStatus() ).isEqualTo( 200 );
            assertThat( server.received( "/nohead" ) ).extracting( FakeHttpServer.Received::method )
                    .containsExactly( "HEAD", "GET" );
        }

        @Test
        void usaCodigoDoGetQuandoHeadNaoESuportado()
        {
            ServiceStatus status = checkOnce( server.url( "/nohead-down" ) );

            assertThat( status.state() ).isEqualTo( State.DOWN );
            assertThat( status.httpStatus() ).isEqualTo( 500 );
        }

        @Test
        void offlineQuandoEstouraOTimeout()
        {
            long start = System.nanoTime();
            ServiceStatus status = checkOnce( server.url( "/slow" ) );

            assertThat( status.state() ).isEqualTo( State.DOWN );
            assertThat( status.httpStatus() ).isNull();
            assertThat( Duration.ofNanos( System.nanoTime() - start ) ).isLessThan( TIMEOUT.multipliedBy( 4 ) );
        }

        @Test
        void offlineQuandoAConexaoERecusada()
        {
            ServiceStatus status = checkOnce( "http://127.0.0.1:" + FakeHttpServer.closedPort() + "/" );

            assertThat( status.state() ).isEqualTo( State.DOWN );
            assertThat( status.httpStatus() ).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource( strings = { "#", "  #  ", "   ", "ftp://nas.local", "nao e uma url", "http://", "mailto:a@b.c" } )
        void desconhecidoQuandoNaoHaUrlHttpValida( String url )
        {
            assertThat( checkOnce( url ).state() ).isEqualTo( State.UNKNOWN );
        }

        @Test
        void verificaTodosOsCardsEmParalelo()
        {
            when( cardRepository.findAll() ).thenReturn( List.of(
                    TestData.card( 1L, "A", server.url( "/slow" ) ),
                    TestData.card( 2L, "B", server.url( "/slow" ) ),
                    TestData.card( 3L, "C", server.url( "/slow" ) ) ) );
            StatusService service = service();

            long start = System.nanoTime();
            service.refreshAll();

            // Em serie levaria 3x o timeout
            assertThat( Duration.ofNanos( System.nanoTime() - start ) ).isLessThan( TIMEOUT.multipliedBy( 3 ) );
            assertThat( service.getAll() ).containsOnlyKeys( 1L, 2L, 3L );
        }
    }

    @Nested
    class Memoria
    {
        @Test
        void comecaVazio()
        {
            assertThat( service().getAll() ).isEmpty();
        }

        @Test
        void removeCardsQueSairamDoCatalogo()
        {
            Card a = TestData.card( 1L, "A", server.url( "/ok" ) );
            Card b = TestData.card( 2L, "B", server.url( "/ok" ) );
            when( cardRepository.findAll() ).thenReturn( List.of( a, b ), List.of( a ) );
            StatusService service = service();

            service.refreshAll();
            assertThat( service.getAll() ).containsOnlyKeys( 1L, 2L );

            service.refreshAll();
            assertThat( service.getAll() ).containsOnlyKeys( 1L );
        }

        @Test
        void evictRemoveOStatus()
        {
            when( cardRepository.findAll() ).thenReturn( List.of( TestData.card( 1L, "A", server.url( "/ok" ) ) ) );
            StatusService service = service();
            service.refreshAll();

            service.evict( 1L );

            assertThat( service.getAll() ).isEmpty();
        }

        @Test
        void getAllDevolveUmaCopia()
        {
            when( cardRepository.findAll() ).thenReturn( List.of( TestData.card( 1L, "A", server.url( "/ok" ) ) ) );
            StatusService service = service();
            service.refreshAll();

            var snapshot = service.getAll();
            service.evict( 1L );

            assertThat( snapshot ).containsOnlyKeys( 1L );
        }

        @Test
        void checkSoonAtualizaOStatusEmSegundoPlano()
        {
            StatusService service = service();

            service.checkSoon( 7L, server.url( "/ok" ) );

            FakeHttpServer.await( () -> service.getAll().containsKey( 7L ) );
            assertThat( service.getAll().get( 7L ).state() ).isEqualTo( State.UP );
        }
    }

    @Nested
    class Avisos
    {
        private Card card;
        private StatusService service;

        @BeforeEach
        void setUp()
        {
            card = TestData.card( 1L, "Jellyfin", server.url( "/toggle" ) );
            when( cardRepository.findAll() ).thenReturn( List.of( card ) );
            service = service();
        }

        /** Roda um ciclo para cada codigo, em sequencia. */
        private void cycles( int... codes )
        {
            for ( int code : codes )
            {
                toggleCode.set( code );
                service.refreshAll();
            }
        }

        @Test
        void servicoSempreOnlineNaoAvisa()
        {
            cycles( 200, 200, 200 );

            verifyNoInteractions( notifier );
        }

        @Test
        void umaFalhaIsoladaNaoAvisa()
        {
            cycles( 200, 503, 200 );

            verifyNoInteractions( notifier );
        }

        @Test
        void falhasNaoSeguidasNaoAvisam()
        {
            cycles( 200, 503, 200, 503, 200 );

            verifyNoInteractions( notifier );
        }

        @Test
        void duasFalhasSeguidasAvisamAQueda()
        {
            cycles( 200, 503, 503 );

            verify( notifier ).notifyDown( "Jellyfin", card.getUrl(), 503 );
        }

        @Test
        void quedaContinuaNaoRepeteOAviso()
        {
            cycles( 200, 503, 503, 503, 503, 503 );

            verify( notifier, times( 1 ) ).notifyDown( any(), any(), any() );
        }

        @Test
        void avisoDeQuedaSemRespostaNaoTemCodigoHttp()
        {
            card.setUrl( "http://127.0.0.1:" + FakeHttpServer.closedPort() + "/" );

            service.refreshAll();
            service.refreshAll();

            verify( notifier ).notifyDown( eq( "Jellyfin" ), eq( card.getUrl() ), eq( null ) );
        }

        @Test
        void retornoAvisaComLatenciaETempoForaDoAr()
        {
            Instant before = Instant.now();
            cycles( 200, 503, 503, 200 );
            Duration maxDowntime = Duration.between( before, Instant.now() );

            verify( notifier ).notifyUp( eq( "Jellyfin" ), eq( card.getUrl() ), anyLong(),
                    argThat( downtime -> downtime != null && !downtime.isNegative()
                            && downtime.compareTo( maxDowntime ) <= 0 ) );
        }

        @Test
        void retornoSoEAvisadoUmaVez()
        {
            cycles( 200, 503, 503, 200, 200, 200 );

            verify( notifier, times( 1 ) ).notifyUp( any(), any(), anyLong(), any() );
        }

        @Test
        void naoAvisaRetornoSemTerAvisadoAQueda()
        {
            cycles( 200, 503, 200 );

            verify( notifier, never() ).notifyUp( any(), any(), anyLong(), any() );
        }

        @Test
        void cadaNovaQuedaGeraUmNovoAviso()
        {
            cycles( 200, 503, 503, 200, 503, 503 );

            verify( notifier, times( 2 ) ).notifyDown( any(), any(), any() );
            verify( notifier, times( 1 ) ).notifyUp( any(), any(), anyLong(), any() );
        }

        @Test
        void servicoJaForaNaInicializacaoEAvisado()
        {
            cycles( 503, 503 );

            verify( notifier ).notifyDown( "Jellyfin", card.getUrl(), 503 );
        }

        @Test
        void servicoOnlineNaInicializacaoNaoGeraAvisoDeRetorno()
        {
            cycles( 200 );

            verify( notifier, never() ).notifyUp( any(), any(), anyLong(), any() );
        }

        @Test
        void limiteConfiguravel()
        {
            service = service( 3 );

            cycles( 200, 503, 503 );
            verify( notifier, never() ).notifyDown( any(), any(), any() );

            cycles( 503 );
            verify( notifier, times( 1 ) ).notifyDown( any(), any(), any() );
        }

        @Test
        void limiteMenorQueUmViraUm()
        {
            service = service( 0 );

            cycles( 200, 503 );

            verify( notifier ).notifyDown( "Jellyfin", card.getUrl(), 503 );
        }

        @Test
        void checkSoonNaoContaParaOsAvisos()
        {
            cycles( 200, 503 );
            Instant lastCheck = service.getAll().get( 1L ).checkedAt();

            service.checkSoon( 1L, card.getUrl() );
            FakeHttpServer.await( () -> service.getAll().get( 1L ).checkedAt().isAfter( lastCheck ) );

            verify( notifier, never() ).notifyDown( any(), any(), any() );

            // O proximo ciclo normal e a 2a falha que conta
            cycles( 503 );
            verify( notifier, times( 1 ) ).notifyDown( any(), any(), any() );
        }

        @Test
        void evictZeraAContagemDeFalhas()
        {
            cycles( 200, 503 );

            service.evict( 1L );
            cycles( 503 );

            verify( notifier, never() ).notifyDown( any(), any(), any() );
        }

        @Test
        void cardSemLinkZeraAContagemDeFalhas()
        {
            cycles( 200, 503 );

            card.setUrl( "#" );
            service.refreshAll();
            card.setUrl( server.url( "/toggle" ) );
            cycles( 503 );

            verify( notifier, never() ).notifyDown( any(), any(), any() );
        }

        @Test
        void cardRemovidoDoCatalogoZeraAContagem()
        {
            cycles( 200, 503 );

            when( cardRepository.findAll() ).thenReturn( List.of(), List.of( card ) );
            service.refreshAll();
            cycles( 503 );

            verify( notifier, never() ).notifyDown( any(), any(), any() );
        }

        @Test
        void avisosSaoIndependentesPorCard()
        {
            Card outro = TestData.card( 2L, "Proxmox", server.url( "/ok" ) );
            when( cardRepository.findAll() ).thenReturn( List.of( card, outro ) );

            cycles( 200, 503, 503 );

            verify( notifier ).notifyDown( eq( "Jellyfin" ), any(), any() );
            verify( notifier, never() ).notifyDown( eq( "Proxmox" ), any(), any() );
        }
    }
}

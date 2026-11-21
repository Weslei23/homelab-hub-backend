package com.homelab.homepage.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/** Servidor HTTP local para simular servicos do homelab e o webhook do Discord. */
public final class FakeHttpServer implements AutoCloseable
{
    /** Requisicao recebida: metodo, caminho, query (decodificada), cabecalho Authorization e corpo. */
    public record Received( String method, String path, String query, String authorization, String body )
    {
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public FakeHttpServer()
    {
        try
        {
            server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( e );
        }
        // Uma thread por requisicao: um handler lento nao bloqueia os demais
        server.setExecutor( Executors.newVirtualThreadPerTaskExecutor() );
        server.start();
    }

    /** Responde sempre com o codigo informado. */
    public FakeHttpServer respond( String path, int status )
    {
        return handle( path, exchange -> reply( exchange, status ) );
    }

    public FakeHttpServer handle( String path, HttpHandler handler )
    {
        server.createContext( path, exchange -> {
            received.add( new Received( exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(), exchange.getRequestHeaders().getFirst( "Authorization" ),
                    new String( exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8 ) ) );
            handler.handle( exchange );
        } );
        return this;
    }

    public static void reply( HttpExchange exchange, int status ) throws IOException
    {
        exchange.sendResponseHeaders( status, -1 );
        exchange.close();
    }

    /** Responde com JSON. */
    public static void replyJson( HttpExchange exchange, int status, String json ) throws IOException
    {
        byte[] bytes = json.getBytes( java.nio.charset.StandardCharsets.UTF_8 );
        exchange.getResponseHeaders().add( "Content-Type", "application/json" );
        exchange.sendResponseHeaders( status, bytes.length );
        exchange.getResponseBody().write( bytes );
        exchange.close();
    }

    public String url( String path )
    {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public List<Received> received()
    {
        return received;
    }

    public List<Received> received( String path )
    {
        return received.stream().filter( r -> r.path().equals( path ) ).toList();
    }

    @Override
    public void close()
    {
        server.stop( 0 );
    }

    /** Porta local em que nada esta escutando (conexao recusada). */
    public static int closedPort()
    {
        try ( ServerSocket socket = new ServerSocket( 0 ) )
        {
            return socket.getLocalPort();
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }

    /** Espera uma condicao assincrona, falhando apos o tempo limite. */
    public static void await( BooleanSupplier condition, Duration timeout )
    {
        long deadline = System.nanoTime() + timeout.toNanos();
        while ( !condition.getAsBoolean() )
        {
            if ( System.nanoTime() > deadline )
            {
                throw new AssertionError( "Condicao nao atendida em " + timeout );
            }
            try
            {
                Thread.sleep( 20 );
            }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                throw new AssertionError( e );
            }
        }
    }

    public static void await( BooleanSupplier condition )
    {
        await( condition, Duration.ofSeconds( 5 ) );
    }
}

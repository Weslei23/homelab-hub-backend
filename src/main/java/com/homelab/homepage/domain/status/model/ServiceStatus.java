package com.homelab.homepage.domain.status.model;

import java.time.Instant;

/**
 * Resultado da ultima verificacao de um card. Fica apenas em memoria.
 *
 * @param state     UP, DOWN ou UNKNOWN (sem link configurado / ainda nao verificado)
 * @param latencyMs tempo de resposta em ms; null quando o servico nao respondeu
 * @param httpStatus codigo HTTP recebido; null quando nao houve resposta
 * @param checkedAt momento da verificacao
 */
public record ServiceStatus( State state, Long latencyMs, Integer httpStatus, Instant checkedAt )
{
    public enum State
    {
        UP, DOWN, UNKNOWN
    }

    public static ServiceStatus up( long latencyMs, int httpStatus )
    {
        return new ServiceStatus( State.UP, latencyMs, httpStatus, Instant.now() );
    }

    public static ServiceStatus down( Integer httpStatus )
    {
        return new ServiceStatus( State.DOWN, null, httpStatus, Instant.now() );
    }

    public static ServiceStatus unknown()
    {
        return new ServiceStatus( State.UNKNOWN, null, null, Instant.now() );
    }
}

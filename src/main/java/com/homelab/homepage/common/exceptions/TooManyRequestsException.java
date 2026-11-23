package com.homelab.homepage.common.exceptions;

/** Limite de tentativas atingido (HTTP 429). */
public class TooManyRequestsException extends RuntimeException
{
    private final long retryAfterSeconds;

    public TooManyRequestsException( String message, long retryAfterSeconds )
    {
        super( message );
        this.retryAfterSeconds = Math.max( 1, retryAfterSeconds );
    }

    public long getRetryAfterSeconds()
    {
        return retryAfterSeconds;
    }
}

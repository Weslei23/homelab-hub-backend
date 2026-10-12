package com.homelab.homepage.exception;

public class ResourceNotFoundException extends RuntimeException
{

    public ResourceNotFoundException( String message )
    {
        super( message );
    }
}

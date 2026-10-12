package com.homelab.homepage.common.exceptions;

public class ResourceNotFoundException extends RuntimeException
{

    public ResourceNotFoundException( String message )
    {
        super( message );
    }
}

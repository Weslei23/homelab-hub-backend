package com.homelab.homepage.domain.media.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Os dois clientes *arr configurados pelo application.yaml (app.media.sonarr / app.media.radarr). */
@Component
public class ArrClients
{
    private final ArrClient sonarr;
    private final ArrClient radarr;

    @Autowired
    public ArrClients(
            @Value( "${app.media.sonarr.url:}" ) String sonarrUrl,
            @Value( "${app.media.sonarr.api-key:}" ) String sonarrKey,
            @Value( "${app.media.radarr.url:}" ) String radarrUrl,
            @Value( "${app.media.radarr.api-key:}" ) String radarrKey,
            @Value( "${app.media.timeout:15s}" ) Duration timeout,
            JsonMapper jsonMapper )
    {
        this( new ArrClient( ArrClient.Kind.SONARR, sonarrUrl, sonarrKey, timeout, jsonMapper ),
                new ArrClient( ArrClient.Kind.RADARR, radarrUrl, radarrKey, timeout, jsonMapper ) );
    }

    public ArrClients( ArrClient sonarr, ArrClient radarr )
    {
        this.sonarr = sonarr;
        this.radarr = radarr;
    }

    public ArrClient sonarr()
    {
        return sonarr;
    }

    public ArrClient radarr()
    {
        return radarr;
    }
}

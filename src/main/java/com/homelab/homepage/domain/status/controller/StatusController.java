package com.homelab.homepage.domain.status.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homelab.homepage.domain.status.model.ServiceStatus;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;
import com.homelab.homepage.domain.status.service.StatusService;

@RestController
@RequestMapping( "/api" )
public class StatusController
{
    private final StatusService statusService;
    private final DiscordNotifier discordNotifier;

    public StatusController( StatusService statusService, DiscordNotifier discordNotifier )
    {
        this.statusService = statusService;
        this.discordNotifier = discordNotifier;
    }

    /** Status de cada card, indexado pelo id do card. */
    @GetMapping( "/status" )
    public Map<Long, ServiceStatus> listStatus()
    {
        return statusService.getAll();
    }

    /** Envia uma mensagem de teste para o webhook do Discord (somente ADMIN). */
    @PostMapping( "/status/test-notification" )
    public ResponseEntity<Map<String, String>> testNotification()
    {
        if ( !discordNotifier.isEnabled() )
        {
            return ResponseEntity.badRequest().body( Map.of( "message", "DISCORD_WEBHOOK_URL nao configurado" ) );
        }
        return discordNotifier.sendTest()
                ? ResponseEntity.ok( Map.of( "message", "Mensagem de teste enviada" ) )
                : ResponseEntity.status( 502 ).body( Map.of( "message", "O Discord recusou a mensagem; veja os logs" ) );
    }
}

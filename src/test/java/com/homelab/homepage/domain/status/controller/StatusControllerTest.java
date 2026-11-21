package com.homelab.homepage.domain.status.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.homelab.homepage.domain.status.model.ServiceStatus;
import com.homelab.homepage.domain.status.model.ServiceStatus.State;
import com.homelab.homepage.domain.status.notify.DiscordNotifier;
import com.homelab.homepage.domain.status.service.StatusService;

class StatusControllerTest
{
    private StatusService statusService;
    private DiscordNotifier discordNotifier;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        statusService = mock( StatusService.class );
        discordNotifier = mock( DiscordNotifier.class );
        mvc = MockMvcBuilders.standaloneSetup( new StatusController( statusService, discordNotifier ) ).build();
    }

    @Test
    void listaOStatusPorIdDoCard() throws Exception
    {
        Instant checkedAt = Instant.parse( "2026-11-20T13:02:00Z" );
        when( statusService.getAll() ).thenReturn( Map.of(
                3L, new ServiceStatus( State.UP, 42L, 200, checkedAt ),
                7L, new ServiceStatus( State.DOWN, null, null, checkedAt ) ) );

        mvc.perform( get( "/api/status" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$['3'].state" ).value( "UP" ) )
                .andExpect( jsonPath( "$['3'].latencyMs" ).value( 42 ) )
                .andExpect( jsonPath( "$['3'].httpStatus" ).value( 200 ) )
                .andExpect( jsonPath( "$['3'].checkedAt" ).value( "2026-11-20T13:02:00Z" ) )
                .andExpect( jsonPath( "$['7'].state" ).value( "DOWN" ) )
                .andExpect( jsonPath( "$['7'].latencyMs" ).doesNotExist() );
    }

    @Test
    void listaVaziaAntesDaPrimeiraVerificacao() throws Exception
    {
        when( statusService.getAll() ).thenReturn( Map.of() );

        mvc.perform( get( "/api/status" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$" ).isEmpty() );
    }

    @Test
    void testeSemWebhookConfiguradoDevolve400() throws Exception
    {
        when( discordNotifier.isEnabled() ).thenReturn( false );

        mvc.perform( post( "/api/status/test-notification" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.message" ).value( "DISCORD_WEBHOOK_URL nao configurado" ) );
        verify( discordNotifier, never() ).sendTest();
    }

    @Test
    void testeAceitoPeloDiscordDevolve200() throws Exception
    {
        when( discordNotifier.isEnabled() ).thenReturn( true );
        when( discordNotifier.sendTest() ).thenReturn( true );

        mvc.perform( post( "/api/status/test-notification" ) )
                .andExpect( status().isOk() )
                .andExpect( jsonPath( "$.message" ).value( "Mensagem de teste enviada" ) );
    }

    @Test
    void testeRecusadoPeloDiscordDevolve502() throws Exception
    {
        when( discordNotifier.isEnabled() ).thenReturn( true );
        when( discordNotifier.sendTest() ).thenReturn( false );

        mvc.perform( post( "/api/status/test-notification" ) )
                .andExpect( status().isBadGateway() )
                .andExpect( jsonPath( "$.message" ).value( "O Discord recusou a mensagem; veja os logs" ) );
    }
}

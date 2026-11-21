package com.homelab.homepage.domain.status.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.homelab.homepage.domain.status.model.ServiceStatus.State;

class ServiceStatusTest
{
    @Test
    void upGuardaLatenciaECodigo()
    {
        Instant before = Instant.now();
        ServiceStatus status = ServiceStatus.up( 42, 200 );

        assertThat( status.state() ).isEqualTo( State.UP );
        assertThat( status.latencyMs() ).isEqualTo( 42L );
        assertThat( status.httpStatus() ).isEqualTo( 200 );
        assertThat( status.checkedAt() ).isAfterOrEqualTo( before );
    }

    @Test
    void downNaoTemLatencia()
    {
        ServiceStatus status = ServiceStatus.down( 502 );

        assertThat( status.state() ).isEqualTo( State.DOWN );
        assertThat( status.latencyMs() ).isNull();
        assertThat( status.httpStatus() ).isEqualTo( 502 );
    }

    @Test
    void downSemRespostaNaoTemCodigo()
    {
        assertThat( ServiceStatus.down( null ).httpStatus() ).isNull();
    }

    @Test
    void unknownNaoTemLatenciaNemCodigo()
    {
        ServiceStatus status = ServiceStatus.unknown();

        assertThat( status.state() ).isEqualTo( State.UNKNOWN );
        assertThat( status.latencyMs() ).isNull();
        assertThat( status.httpStatus() ).isNull();
        assertThat( status.checkedAt() ).isNotNull();
    }
}

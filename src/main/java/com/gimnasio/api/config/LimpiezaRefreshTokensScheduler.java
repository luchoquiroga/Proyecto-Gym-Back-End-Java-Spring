package com.gimnasio.api.config;

import com.gimnasio.api.security.LimpiezaRefreshTokensService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado que borra los refresh tokens vencidos (ver LimpiezaRefreshTokensService).
 */
@Component
@RequiredArgsConstructor
public class LimpiezaRefreshTokensScheduler {

    private final LimpiezaRefreshTokensService limpiezaRefreshTokensService;

    // Todos los días a las 03:00 de Argentina, lejos de la corrida de vencimientos de las
    // 00:00 y de las horas en que el gimnasio cobra.
    @Scheduled(cron = "0 0 3 * * *", zone = ZonaHorariaConfig.ZONA_GIMNASIO)
    public void borrarVencidos() {
        limpiezaRefreshTokensService.borrarVencidos();
    }
}

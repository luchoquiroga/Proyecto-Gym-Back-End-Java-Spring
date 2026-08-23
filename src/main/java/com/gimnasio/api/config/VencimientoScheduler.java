package com.gimnasio.api.config;

import com.gimnasio.api.services.VencimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado que dispara la verificación diaria de vencimientos de pago.
 */
@Component
@RequiredArgsConstructor
public class VencimientoScheduler {

    private final VencimientoService vencimientoService;

    // Se ejecuta todos los días a las 00:00 (medianoche)
    @Scheduled(cron = "0 0 0 * * *")
    public void verificarVencimientos() {
        vencimientoService.actualizarEstadosPorVencimiento();
    }
}

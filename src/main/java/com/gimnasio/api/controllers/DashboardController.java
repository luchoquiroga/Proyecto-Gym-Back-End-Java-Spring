package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para los reportes del dashboard administrativo.
 * Todos sus endpoints están restringidos al rol ADMIN (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Devuelve el total de ganancias y la cantidad de pagos de un mes.
     * Sin parámetros, informa el mes en curso.
     */
    @GetMapping("/ganancias-mensuales")
    public ResponseEntity<GananciasMensualesResponse> obtenerGananciasMensuales(
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes) {
        return ResponseEntity.ok(dashboardService.obtenerGananciasMensuales(anio, mes));
    }
}

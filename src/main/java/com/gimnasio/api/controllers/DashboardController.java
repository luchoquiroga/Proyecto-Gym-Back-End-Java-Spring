package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.dto.SociosPorEstadoResponse;
import com.gimnasio.api.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Controlador REST para los reportes del dashboard administrativo.
 * Todos sus endpoints están restringidos al rol ADMIN (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("uuuu-MM");

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

    /**
     * Devuelve las ganancias de cada mes de un rango ({@code ?desde=2025-10&hasta=2026-09},
     * los dos inclusive), un elemento por mes y también los meses sin cobros.
     * Sin parámetros, informa los últimos 12 meses hasta el actual.
     */
    @GetMapping("/ganancias-por-mes")
    public ResponseEntity<List<GananciasMensualesResponse>> obtenerGananciasPorMes(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return ResponseEntity.ok(dashboardService.obtenerGananciasPorMes(
                parsearMes("desde", desde), parsearMes("hasta", hasta)));
    }

    /**
     * Convierte el parámetro AAAA-MM en un YearMonth. Se recibe como String y se parsea a mano
     * para poder responder un 400 que diga qué parámetro está mal y qué formato se espera;
     * con la conversión automática de Spring el error sería genérico.
     */
    private YearMonth parsearMes(String nombreParametro, String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return YearMonth.parse(valor, FORMATO_MES);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "El parámetro '" + nombreParametro + "' debe tener el formato AAAA-MM");
        }
    }

    /**
     * Devuelve cuántos socios hay hoy en cada estado (activos, morosos, inactivos).
     */
    @GetMapping("/socios")
    public ResponseEntity<SociosPorEstadoResponse> contarSociosPorEstado() {
        return ResponseEntity.ok(dashboardService.contarSociosPorEstado());
    }
}

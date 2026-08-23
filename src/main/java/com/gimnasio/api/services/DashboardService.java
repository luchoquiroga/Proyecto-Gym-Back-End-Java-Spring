package com.gimnasio.api.services;

import com.gimnasio.api.dto.GananciasMensualesResponse;

/**
 * Define las operaciones de negocio para los reportes del dashboard administrativo.
 */
public interface DashboardService {

    /**
     * Calcula el total de ganancias (suma de montos abonados) y la cantidad de pagos
     * registrados en un mes puntual.
     * @param anio Año a consultar (si es nulo, se usa el año actual).
     * @param mes Mes a consultar, de 1 a 12 (si es nulo, se usa el mes actual).
     * @return Resumen con el total de ganancias y la cantidad de pagos del período.
     */
    GananciasMensualesResponse obtenerGananciasMensuales(Integer anio, Integer mes);
}

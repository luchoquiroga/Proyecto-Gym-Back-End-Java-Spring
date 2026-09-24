package com.gimnasio.api.services;

import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.dto.SociosPorEstadoResponse;

import java.time.YearMonth;
import java.util.List;

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

    /**
     * Calcula las ganancias de cada mes de un rango, con la misma regla que
     * {@link #obtenerGananciasMensuales}. Devuelve todos los meses del rango, del más viejo
     * al más nuevo, incluidos los que no tuvieron cobros (en cero).
     * @param desde Primer mes, inclusive (si es nulo, 11 meses antes de {@code hasta}).
     * @param hasta Último mes, inclusive (si es nulo, el mes en curso).
     * @return Un resumen por mes del rango.
     * @throws IllegalArgumentException si {@code desde} es posterior a {@code hasta} o si el
     *         rango supera el máximo de meses permitido.
     */
    List<GananciasMensualesResponse> obtenerGananciasPorMes(YearMonth desde, YearMonth hasta);

    /**
     * Cuenta los socios que hay hoy en cada estado. El estado contado es el persistido,
     * que la corrida diaria de vencimientos mantiene al día.
     * @return Cantidad de socios activos, morosos e inactivos.
     */
    SociosPorEstadoResponse contarSociosPorEstado();
}

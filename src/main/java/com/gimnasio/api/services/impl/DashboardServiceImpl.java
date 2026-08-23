package com.gimnasio.api.services.impl;

import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Implementación de la lógica de negocio para los reportes del dashboard administrativo.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final PagoRepository pagoRepository;

    @Override
    @Transactional(readOnly = true)
    public GananciasMensualesResponse obtenerGananciasMensuales(Integer anio, Integer mes) {
        LocalDate hoy = LocalDate.now();
        int anioEfectivo = (anio != null) ? anio : hoy.getYear();
        int mesEfectivo = (mes != null) ? mes : hoy.getMonthValue();

        // YearMonth valida el rango del mes (1-12) y calcula el último día correctamente
        // según el mes y el año (bisiestos incluidos).
        YearMonth periodo = YearMonth.of(anioEfectivo, mesEfectivo);
        LocalDate inicio = periodo.atDay(1);
        LocalDate fin = periodo.atEndOfMonth();

        double totalGanancias = pagoRepository.sumarMontoAbonadoEntre(inicio, fin);
        long cantidadPagos = pagoRepository.countByFechaPagoBetween(inicio, fin);

        return new GananciasMensualesResponse(anioEfectivo, mesEfectivo, totalGanancias, cantidadPagos);
    }
}

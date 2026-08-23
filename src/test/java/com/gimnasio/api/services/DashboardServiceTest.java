package com.gimnasio.api.services;

import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.impl.DashboardServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    @DisplayName("Debe calcular ganancias y cantidad de pagos del mes solicitado")
    void obtenerGananciasMensuales_conAnioYMes_deberiaCalcularCorrectamente() {
        LocalDate inicio = LocalDate.of(2026, 8, 1);
        LocalDate fin = LocalDate.of(2026, 8, 31);

        when(pagoRepository.sumarMontoAbonadoEntre(inicio, fin)).thenReturn(97500.0);
        when(pagoRepository.countByFechaPagoBetween(inicio, fin)).thenReturn(3L);

        GananciasMensualesResponse resultado = dashboardService.obtenerGananciasMensuales(2026, 8);

        assertEquals(2026, resultado.getAnio());
        assertEquals(8, resultado.getMes());
        assertEquals(97500.0, resultado.getTotalGanancias());
        assertEquals(3L, resultado.getCantidadPagos());
    }

    @Test
    @DisplayName("Sin año ni mes, debe usar el período actual")
    void obtenerGananciasMensuales_sinParametros_deberiaUsarMesActual() {
        YearMonth mesActual = YearMonth.now();
        LocalDate inicio = mesActual.atDay(1);
        LocalDate fin = mesActual.atEndOfMonth();

        when(pagoRepository.sumarMontoAbonadoEntre(inicio, fin)).thenReturn(0.0);
        when(pagoRepository.countByFechaPagoBetween(inicio, fin)).thenReturn(0L);

        GananciasMensualesResponse resultado = dashboardService.obtenerGananciasMensuales(null, null);

        assertEquals(mesActual.getYear(), resultado.getAnio());
        assertEquals(mesActual.getMonthValue(), resultado.getMes());
        assertEquals(0.0, resultado.getTotalGanancias());
        assertEquals(0L, resultado.getCantidadPagos());
    }

    @Test
    @DisplayName("Un mes fuera de rango (ej. 13) debe rechazarse")
    void obtenerGananciasMensuales_conMesInvalido_deberiaLanzarExcepcion() {
        assertThrows(DateTimeException.class,
                () -> dashboardService.obtenerGananciasMensuales(2026, 13));
    }
}

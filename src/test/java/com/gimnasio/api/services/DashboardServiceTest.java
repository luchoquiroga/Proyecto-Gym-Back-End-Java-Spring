package com.gimnasio.api.services;

import com.gimnasio.api.config.ZonaHorariaConfig;
import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.dto.SociosPorEstadoResponse;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    private DashboardServiceImpl dashboardService;

    // 30/09/2026 a las 22:00 en Argentina, que en UTC ya es el 1/10: el horario en que
    // el backend, con la zona de la JVM, vivía en el día (y el mes) siguiente.
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-01T01:00:00Z"), ZoneId.of(ZonaHorariaConfig.ZONA_GIMNASIO));
    private static final LocalDate HOY = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(pagoRepository, clienteRepository, RELOJ);
    }

    @Test
    @DisplayName("Debe calcular ganancias y cantidad de pagos del mes solicitado")
    void obtenerGananciasMensuales_conAnioYMes_deberiaCalcularCorrectamente() {
        LocalDate inicio = LocalDate.of(2026, 8, 1);
        LocalDate fin = LocalDate.of(2026, 8, 31);

        when(pagoRepository.sumarMontoAbonadoEntre(inicio, fin)).thenReturn(97500.0);
        when(pagoRepository.countByFechaPagoBetweenAndAnuladoFalse(inicio, fin)).thenReturn(3L);

        GananciasMensualesResponse resultado = dashboardService.obtenerGananciasMensuales(2026, 8);

        assertEquals(2026, resultado.getAnio());
        assertEquals(8, resultado.getMes());
        assertEquals(97500.0, resultado.getTotalGanancias());
        assertEquals(3L, resultado.getCantidadPagos());
    }

    @Test
    @DisplayName("Sin año ni mes, debe usar el mes en curso de Argentina aunque en UTC ya sea el siguiente")
    void obtenerGananciasMensuales_sinParametros_deberiaUsarMesActual() {
        YearMonth mesActual = YearMonth.of(2026, 9);
        LocalDate inicio = mesActual.atDay(1);
        LocalDate fin = mesActual.atEndOfMonth();

        when(pagoRepository.sumarMontoAbonadoEntre(inicio, fin)).thenReturn(0.0);
        when(pagoRepository.countByFechaPagoBetweenAndAnuladoFalse(inicio, fin)).thenReturn(0L);

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

    @Test
    @DisplayName("Debe contar los socios de cada estado")
    void contarSociosPorEstado_deberiaDevolverUnConteoPorEstado() {
        when(clienteRepository.countByEstado(EstadoCliente.ACTIVO)).thenReturn(12L);
        when(clienteRepository.countByEstado(EstadoCliente.MOROSO)).thenReturn(3L);
        when(clienteRepository.countByEstado(EstadoCliente.INACTIVO)).thenReturn(5L);

        SociosPorEstadoResponse resultado = dashboardService.contarSociosPorEstado();

        assertEquals(12L, resultado.getActivos());
        assertEquals(3L, resultado.getMorosos());
        assertEquals(5L, resultado.getInactivos());
    }
}

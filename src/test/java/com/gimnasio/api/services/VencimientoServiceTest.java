package com.gimnasio.api.services;

import com.gimnasio.api.config.ZonaHorariaConfig;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.impl.VencimientoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VencimientoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    private VencimientoServiceImpl vencimientoService;

    // 30/09/2026 a las 22:00 en Argentina, que en UTC ya es el 1/10: el horario en que
    // el backend, con la zona de la JVM, vivía en el día (y el mes) siguiente.
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-01T01:00:00Z"), ZoneId.of(ZonaHorariaConfig.ZONA_GIMNASIO));
    private static final LocalDate HOY = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        vencimientoService = new VencimientoServiceImpl(pagoRepository, clienteRepository, RELOJ);
    }

    private final Plan planMensual = new Plan(1, "Pase Mensual", 32500.0, 30);

    private Pago pagoConVencimientoHace(int dias, Cliente cliente) {
        LocalDate fechaVencimiento = HOY.minusDays(dias);
        return new Pago(1, cliente, planMensual, 32500.0, fechaVencimiento.minusDays(30), fechaVencimiento, null);
    }

    @Test
    @DisplayName("Cliente ACTIVO con pago vencido hace 1 día debe pasar a MOROSO")
    void unDiaVencido_deberiaPasarAMoroso() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.ACTIVO, null);
        Pago ultimoPago = pagoConVencimientoHace(1, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.MOROSO, cliente.getEstado());
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Cliente con pago vencido hace 5 días o más debe pasar a INACTIVO")
    void cincoDiasVencido_deberiaPasarAInactivo() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.MOROSO, null);
        Pago ultimoPago = pagoConVencimientoHace(5, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.INACTIVO, cliente.getEstado());
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Cliente con pago todavía vigente no debe cambiar de estado")
    void pagoVigente_noDeberiaCambiarEstado() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.ACTIVO, null);
        Pago ultimoPago = pagoConVencimientoHace(-2, cliente); // vence en 2 días
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.ACTIVO, cliente.getEstado());
        verify(clienteRepository, never()).save(cliente);
    }

    @Test
    @DisplayName("Cliente ya INACTIVO no debe revertirse a MOROSO aunque los días vencidos bajen de 5")
    void clienteYaInactivo_noDeberiaRevertirse() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.INACTIVO, null);
        Pago ultimoPago = pagoConVencimientoHace(1, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.INACTIVO, cliente.getEstado());
        verify(clienteRepository, never()).save(cliente);
    }

    @Test
    @DisplayName("A las 22:00 de Argentina del día en que vence, el socio sigue al día")
    void elDiaDelVencimiento_aLas22_noDeberiaPasarAMoroso() {
        // Con el reloj de la JVM en UTC ya sería el día siguiente y pasaría a MOROSO.
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.ACTIVO, null);
        Pago ultimoPago = pagoConVencimientoHace(0, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.ACTIVO, cliente.getEstado());
        verify(clienteRepository, never()).save(cliente);
    }

    @Test
    @DisplayName("Recalcular con un pago todavía vigente deja al socio ACTIVO")
    void recalcularEstadoDe_conPagoVigente_noDeberiaCambiarEstado() {
        // Regresión: calcularEstadoPorDiasVencido devuelve null ("no tocar") y dentro de un
        // Optional.map ese null se volvía un Optional vacío, que caía en el orElse(INACTIVO).
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.ACTIVO, null);
        Pago pagoVigente = pagoConVencimientoHace(-30, cliente);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(pagoRepository.findTopByClienteIdAndAnuladoFalseOrderByFechaVencimientoDesc(1))
                .thenReturn(Optional.of(pagoVigente));

        vencimientoService.recalcularEstadoDe(1);

        assertEquals(EstadoCliente.ACTIVO, cliente.getEstado());
        verify(clienteRepository, never()).save(cliente);
    }

    @Test
    @DisplayName("Recalcular sin ningún pago válido pasa al socio a INACTIVO")
    void recalcularEstadoDe_sinPagos_deberiaPasarAInactivo() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.ACTIVO, null);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(pagoRepository.findTopByClienteIdAndAnuladoFalseOrderByFechaVencimientoDesc(1))
                .thenReturn(Optional.empty());

        vencimientoService.recalcularEstadoDe(1);

        assertEquals(EstadoCliente.INACTIVO, cliente.getEstado());
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Recalcular con el último pago vencido hace 2 días pasa al socio a MOROSO")
    void recalcularEstadoDe_conPagoVencidoHaceDosDias_deberiaPasarAMoroso() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.ACTIVO, null);
        Pago pagoVencido = pagoConVencimientoHace(2, cliente);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(pagoRepository.findTopByClienteIdAndAnuladoFalseOrderByFechaVencimientoDesc(1))
                .thenReturn(Optional.of(pagoVencido));

        vencimientoService.recalcularEstadoDe(1);

        assertEquals(EstadoCliente.MOROSO, cliente.getEstado());
    }
}

package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.impl.VencimientoServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

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

    @InjectMocks
    private VencimientoServiceImpl vencimientoService;

    private final Plan planMensual = new Plan(1, "Pase Mensual", 32500.0, 30);

    private Pago pagoConVencimientoHace(int dias, Cliente cliente) {
        LocalDate fechaVencimiento = LocalDate.now().minusDays(dias);
        return new Pago(1, cliente, planMensual, 32500.0, fechaVencimiento.minusDays(30), fechaVencimiento);
    }

    @Test
    @DisplayName("Cliente ACTIVO con pago vencido hace 1 día debe pasar a MOROSO")
    void unDiaVencido_deberiaPasarAMoroso() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", null, null, EstadoCliente.ACTIVO);
        Pago ultimoPago = pagoConVencimientoHace(1, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.MOROSO, cliente.getEstado());
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Cliente con pago vencido hace 5 días o más debe pasar a INACTIVO")
    void cincoDiasVencido_deberiaPasarAInactivo() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", null, null, EstadoCliente.MOROSO);
        Pago ultimoPago = pagoConVencimientoHace(5, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.INACTIVO, cliente.getEstado());
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Cliente con pago todavía vigente no debe cambiar de estado")
    void pagoVigente_noDeberiaCambiarEstado() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", null, null, EstadoCliente.ACTIVO);
        Pago ultimoPago = pagoConVencimientoHace(-2, cliente); // vence en 2 días
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.ACTIVO, cliente.getEstado());
        verify(clienteRepository, never()).save(cliente);
    }

    @Test
    @DisplayName("Cliente ya INACTIVO no debe revertirse a MOROSO aunque los días vencidos bajen de 5")
    void clienteYaInactivo_noDeberiaRevertirse() {
        Cliente cliente = new Cliente(1, "Lucía", "Pérez", "11223344", null, null, EstadoCliente.INACTIVO);
        Pago ultimoPago = pagoConVencimientoHace(1, cliente);
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(ultimoPago));

        vencimientoService.actualizarEstadosPorVencimiento();

        assertEquals(EstadoCliente.INACTIVO, cliente.getEstado());
        verify(clienteRepository, never()).save(cliente);
    }
}

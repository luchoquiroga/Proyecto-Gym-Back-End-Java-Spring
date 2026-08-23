package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.services.impl.PagoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PagoServiceImpl pagoService;

    private Cliente clienteInactivo;
    private Plan planMensual;

    @BeforeEach
    void setUp() {
        clienteInactivo = new Cliente(1, "Lucía", "Pérez", "11223344", EstadoCliente.INACTIVO);
        planMensual = new Plan(1, "Pase Mensual", 32500.0, 30);
    }

    @Test
    @DisplayName("Registrar pago debe calcular vencimiento exacto y cambiar estado del cliente a ACTIVO")
    void registrarPago_deberiaCalcularVencimientoYActivarCliente() {
        LocalDate fechaPago = LocalDate.of(2026, 8, 1);
        LocalDate fechaVencimientoEsperada = LocalDate.of(2026, 8, 31); // 1 de agosto + 30 días

        when(clienteRepository.findById(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, 32500.0, fechaPago);

        // 1. Verificar datos del pago
        assertNotNull(pagoRegistrado);
        assertEquals(32500.0, pagoRegistrado.getMontoAbonado());
        assertEquals(fechaPago, pagoRegistrado.getFechaPago());
        assertEquals(fechaVencimientoEsperada, pagoRegistrado.getFechaVencimiento());

        // 2. Verificar que el cliente fue activado
        assertEquals(EstadoCliente.ACTIVO, clienteInactivo.getEstado());

        // 3. Verificar que se persistió el pago y la actualización del cliente
        verify(pagoRepository, times(1)).save(any(Pago.class));
        verify(clienteRepository, times(1)).save(clienteInactivo);
    }

    @Test
    @DisplayName("Debe lanzar excepción si el cliente no existe al registrar un pago")
    void registrarPago_cuandoClienteNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            pagoService.registrarPago(99, 1, 32500.0, LocalDate.now());
        });

        assertTrue(ex.getMessage().contains("Cliente no encontrado con ID 99"));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el plan no existe al registrar un pago")
    void registrarPago_cuandoPlanNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            pagoService.registrarPago(1, 99, 32500.0, LocalDate.now());
        });

        assertTrue(ex.getMessage().contains("Plan no encontrado con ID 99"));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    @DisplayName("Debe buscar pagos por fragmento de nombre de cliente")
    void buscarPagosPorNombreCliente_deberiaRetornarLista() {
        Pago pago = new Pago(1, clienteInactivo, planMensual, 32500.0, LocalDate.now(), LocalDate.now().plusDays(30));
        when(pagoRepository.findByClienteNombreContainingIgnoreCase("Lucía")).thenReturn(List.of(pago));

        List<Pago> resultados = pagoService.buscarPagosPorNombreCliente("Lucía");

        assertEquals(1, resultados.size());
        assertEquals("Lucía", resultados.get(0).getCliente().getNombre());
        verify(pagoRepository, times(1)).findByClienteNombreContainingIgnoreCase("Lucía");
    }
}

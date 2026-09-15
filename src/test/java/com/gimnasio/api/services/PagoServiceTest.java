package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
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

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PagoServiceImpl pagoService;

    private Cliente clienteInactivo;
    private Plan planMensual;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        clienteInactivo = new Cliente(1, "Lucía", "Pérez", "11223344", null, null, EstadoCliente.INACTIVO, null);
        planMensual = new Plan(1, "Pase Mensual", 32500.0, 30);
        cajero = new Usuario(7, "gerencia1", "hash-irrelevante", RolUsuario.GERENCIA);
    }

    @Test
    @DisplayName("Registrar pago debe calcular vencimiento exacto y cambiar estado del cliente a ACTIVO")
    void registrarPago_deberiaCalcularVencimientoYActivarCliente() {
        // Fecha de pago vigente: el vencimiento cae en el futuro, así que el pago sí
        // deja al cliente al día (ver el test del pago retroactivo para el otro caso).
        LocalDate fechaPago = LocalDate.now();
        LocalDate fechaVencimientoEsperada = fechaPago.plusDays(30);

        when(clienteRepository.findById(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, 32500.0, fechaPago, 7);

        // 1. Verificar datos del pago
        assertNotNull(pagoRegistrado);
        assertEquals(32500.0, pagoRegistrado.getMontoAbonado());
        assertEquals(fechaPago, pagoRegistrado.getFechaPago());
        assertEquals(fechaVencimientoEsperada, pagoRegistrado.getFechaVencimiento());

        // 2. Verificar que el cliente fue activado
        assertEquals(EstadoCliente.ACTIVO, clienteInactivo.getEstado());

        // 3. Verificar que quedó el rastro de quién cobró
        assertEquals(cajero, pagoRegistrado.getRegistradoPor());

        // 4. Verificar que se persistió el pago y la actualización del cliente
        verify(pagoRepository, times(1)).save(any(Pago.class));
        verify(clienteRepository, times(1)).save(clienteInactivo);
    }

    @Test
    @DisplayName("Un pago retroactivo cuyo período ya venció no debe reactivar al cliente")
    void registrarPago_cuandoElPeriodoYaVencio_noDeberiaActivarCliente() {
        // Pago cargado con fecha vieja: 60 días atrás + 30 de plan = vencido hace 30.
        LocalDate fechaPago = LocalDate.now().minusDays(60);

        when(clienteRepository.findById(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, 32500.0, fechaPago, 7);

        // El pago se registra (es un hecho contable real), pero no pone al día a nadie.
        assertNotNull(pagoRegistrado);
        assertEquals(EstadoCliente.INACTIVO, clienteInactivo.getEstado());
        verify(pagoRepository, times(1)).save(any(Pago.class));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Un pago menor al precio del plan debe rechazarse y no registrarse")
    void registrarPago_cuandoMontoEsMenorAlPrecioDelPlan_deberiaRechazar() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));

        // $1 por un plan de $32500: antes esto daba de alta un mes completo.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            pagoService.registrarPago(1, 1, 1.0, LocalDate.now(), 7);
        });

        assertTrue(ex.getMessage().contains("menor al precio del plan"));
        assertEquals(EstadoCliente.INACTIVO, clienteInactivo.getEstado());
        verify(pagoRepository, never()).save(any(Pago.class));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Sin monto explícito se cobra el precio oficial del plan")
    void registrarPago_sinMonto_deberiaCobrarPrecioDelPlan() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, null, LocalDate.now(), 7);

        assertEquals(planMensual.getPrecio(), pagoRegistrado.getMontoAbonado());
    }

    @Test
    @DisplayName("Debe lanzar excepción si el cliente no existe al registrar un pago")
    void registrarPago_cuandoClienteNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            pagoService.registrarPago(99, 1, 32500.0, LocalDate.now(), 7);
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
            pagoService.registrarPago(1, 99, 32500.0, LocalDate.now(), 7);
        });

        assertTrue(ex.getMessage().contains("Plan no encontrado con ID 99"));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    @DisplayName("Debe buscar pagos por fragmento de nombre de cliente")
    void buscarPagosPorNombreCliente_deberiaRetornarLista() {
        Pago pago = new Pago(1, clienteInactivo, planMensual, 32500.0, LocalDate.now(), LocalDate.now().plusDays(30), cajero);
        when(pagoRepository.findByClienteNombreContainingIgnoreCase("Lucía")).thenReturn(List.of(pago));

        List<Pago> resultados = pagoService.buscarPagosPorNombreCliente("Lucía");

        assertEquals(1, resultados.size());
        assertEquals("Lucía", resultados.get(0).getCliente().getNombre());
        verify(pagoRepository, times(1)).findByClienteNombreContainingIgnoreCase("Lucía");
    }
}

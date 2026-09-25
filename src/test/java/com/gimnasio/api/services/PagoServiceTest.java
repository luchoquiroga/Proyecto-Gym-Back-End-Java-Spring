package com.gimnasio.api.services;

import com.gimnasio.api.config.ZonaHorariaConfig;
import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Mock
    private VencimientoService vencimientoService;

    private PagoServiceImpl pagoService;

    // 30/09/2026 a las 22:00 en Argentina, que en UTC ya es el 1/10: el horario en que
    // el backend, con la zona de la JVM, vivía en el día (y el mes) siguiente.
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-01T01:00:00Z"), ZoneId.of(ZonaHorariaConfig.ZONA_GIMNASIO));
    private static final LocalDate HOY = LocalDate.of(2026, 9, 30);

    private Cliente clienteInactivo;
    private Plan planMensual;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        pagoService = new PagoServiceImpl(pagoRepository, clienteRepository, planRepository,
                usuarioRepository, vencimientoService, RELOJ);
        clienteInactivo = new Cliente(1, "Lucía", "Pérez", "11223344", "11223344", null, null, EstadoCliente.INACTIVO, null);
        planMensual = new Plan(1, "Pase Mensual", 32500.0, 30);
        cajero = new Usuario(7, "gerencia1", "hash-irrelevante", RolUsuario.GERENCIA);
    }

    @Test
    @DisplayName("Registrar pago debe calcular vencimiento exacto y cambiar estado del cliente a ACTIVO")
    void registrarPago_deberiaCalcularVencimientoYActivarCliente() {
        // Fecha de pago vigente: el vencimiento cae en el futuro, así que el pago sí
        // deja al cliente al día (ver el test del pago retroactivo para el otro caso).
        LocalDate fechaPago = HOY;
        LocalDate fechaVencimientoEsperada = fechaPago.plusDays(30);

        when(clienteRepository.findByIdParaActualizar(1)).thenReturn(Optional.of(clienteInactivo));
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
        LocalDate fechaPago = HOY.minusDays(60);

        when(clienteRepository.findByIdParaActualizar(1)).thenReturn(Optional.of(clienteInactivo));
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
        when(clienteRepository.findByIdParaActualizar(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));

        // $1 por un plan de $32500: antes esto daba de alta un mes completo.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            pagoService.registrarPago(1, 1, 1.0, HOY, 7);
        });

        assertTrue(ex.getMessage().contains("menor al precio del plan"));
        assertEquals(EstadoCliente.INACTIVO, clienteInactivo.getEstado());
        verify(pagoRepository, never()).save(any(Pago.class));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Sin monto explícito se cobra el precio oficial del plan")
    void registrarPago_sinMonto_deberiaCobrarPrecioDelPlan() {
        when(clienteRepository.findByIdParaActualizar(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, null, HOY, 7);

        assertEquals(planMensual.getPrecio(), pagoRegistrado.getMontoAbonado());
    }

    @Test
    @DisplayName("Debe lanzar excepción si el cliente no existe al registrar un pago")
    void registrarPago_cuandoClienteNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findByIdParaActualizar(99)).thenReturn(Optional.empty());

        RecursoNoEncontradoException ex = assertThrows(RecursoNoEncontradoException.class, () -> {
            pagoService.registrarPago(99, 1, 32500.0, HOY, 7);
        });

        assertTrue(ex.getMessage().contains("Cliente no encontrado con ID 99"));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el plan no existe al registrar un pago")
    void registrarPago_cuandoPlanNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findByIdParaActualizar(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(99)).thenReturn(Optional.empty());

        RecursoNoEncontradoException ex = assertThrows(RecursoNoEncontradoException.class, () -> {
            pagoService.registrarPago(1, 99, 32500.0, HOY, 7);
        });

        assertTrue(ex.getMessage().contains("Plan no encontrado con ID 99"));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    @DisplayName("Un cobro sin fecha a las 22:00 de Argentina queda con la fecha de hoy, no de mañana")
    void registrarPago_sinFecha_deberiaUsarElDiaDeArgentina() {
        prepararCobro();

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, null, null, 7);

        assertEquals(HOY, pagoRegistrado.getFechaPago());
        assertEquals(HOY.plusDays(30), pagoRegistrado.getFechaVencimiento());
    }

    @Test
    @DisplayName("Un cobro anticipado arranca cuando termina el período vigente")
    void registrarPago_conPeriodoVigente_deberiaEncadenarse() {
        prepararCobro();
        LocalDate vencimientoVigente = HOY.plusDays(20);
        when(pagoRepository.findVencimientoVigenteAl(1, HOY)).thenReturn(Optional.of(vencimientoVigente));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, null, HOY, 7);

        // La plata entró hoy (caja y dashboard no cambian), pero el período nuevo
        // arranca cuando termina el anterior: el socio no pierde sus 20 días.
        assertEquals(HOY, pagoRegistrado.getFechaPago());
        assertEquals(vencimientoVigente.plusDays(30), pagoRegistrado.getFechaVencimiento());
    }

    @Test
    @DisplayName("Con el socio ya vencido, el período arranca el día del cobro")
    void registrarPago_conPeriodoVencido_deberiaArrancarEnLaFechaDePago() {
        prepararCobro();
        when(pagoRepository.findVencimientoVigenteAl(1, HOY)).thenReturn(Optional.of(HOY.minusDays(3)));

        Pago pagoRegistrado = pagoService.registrarPago(1, 1, null, HOY, 7);

        assertEquals(HOY.plusDays(30), pagoRegistrado.getFechaVencimiento());
    }

    @Test
    @DisplayName("No se puede anular un pago si otro se cobró durante su período")
    void anular_conPagoCobradoDuranteSuPeriodo_deberiaRechazar() {
        Pago pago = new Pago(10, clienteInactivo, planMensual, 32500.0, HOY.minusDays(10), HOY.plusDays(20), null);
        Pago posterior = new Pago(11, clienteInactivo, planMensual, 32500.0, HOY, HOY.plusDays(50), null);
        when(pagoRepository.findById(10)).thenReturn(Optional.of(pago));
        when(pagoRepository.findCobradosDuranteElPeriodo(1, 10, HOY.minusDays(10), HOY.plusDays(20)))
                .thenReturn(List.of(posterior));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                pagoService.anular(10, "cargado dos veces", 7));

        assertTrue(ex.getMessage().contains("el pago 11 se cobró durante su período"));
        assertFalse(pago.isAnulado());
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    private void prepararCobro() {
        when(clienteRepository.findByIdParaActualizar(1)).thenReturn(Optional.of(clienteInactivo));
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(cajero));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}

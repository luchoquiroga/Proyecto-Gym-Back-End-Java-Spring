package com.gimnasio.api.services;

import com.gimnasio.api.dto.ClienteResponse;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.impl.ClienteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PagoRepository pagoRepository;

    // Se usa una instancia real (no un mock) por el mismo motivo que en UsuarioServiceTest:
    // el hashing no es determinístico en su salida, mockearlo no aportaría nada.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ClienteServiceImpl clienteService;

    private Cliente clientePrueba;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteServiceImpl(clienteRepository, pagoRepository, passwordEncoder);
        clientePrueba = new Cliente(1, "Carlos", "Gómez", "123456789", null, null, EstadoCliente.INACTIVO, null);
    }

    @Test
    @DisplayName("Debe crear un cliente con estado inicial INACTIVO")
    void crear_deberiaGuardarClienteConEstadoInactivo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotNull(resultado);
        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Debe crear un cliente sin email ni contraseña (alta desde la app de escritorio)")
    void crear_sinCredenciales_deberiaGuardarClienteSinEmailNiContrasena() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNull(resultado.getEmail());
        assertNull(resultado.getContrasena());
        verify(clienteRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("Crear sin credenciales debe generar un código de activación para que el cliente complete /registro después")
    void crear_sinCredenciales_deberiaGenerarCodigoDeActivacion() {
        when(clienteRepository.existsByCodigoActivacion(any())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotNull(resultado.getCodigoActivacion());
    }

    @Test
    @DisplayName("Crear con credenciales directas no debe generar código de activación (no hace falta /registro)")
    void crear_conCredenciales_noDeberiaGenerarCodigoDeActivacion() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", "carlos@mail.com", "clave123", null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNull(resultado.getCodigoActivacion());
    }

    @Test
    @DisplayName("Crear debe ignorar cualquier id enviado en el body (no debe poder pisar otra fila)")
    void crear_conIdEnviado_deberiaIgnorarlo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente conIdAjeno = new Cliente(99, "Carlos", "Gómez", "123456789", null, null, null, null);
        Cliente resultado = clienteService.crear(conIdAjeno);

        assertNull(resultado.getId());
    }

    @Test
    @DisplayName("Crear debe forzar estado INACTIVO aunque el body envíe otro estado explícito")
    void crear_conEstadoExplicito_deberiaForzarInactivo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente conEstadoActivo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, EstadoCliente.ACTIVO, null);
        Cliente resultado = clienteService.crear(conEstadoActivo);

        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
    }

    @Test
    @DisplayName("Debe hashear la contraseña cuando el cliente se registra con email y contraseña")
    void crear_conCredenciales_deberiaHashearLaContrasena() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", "carlos@mail.com", "claveEnTextoPlano", null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotEquals("claveEnTextoPlano", resultado.getContrasena());
        assertTrue(passwordEncoder.matches("claveEnTextoPlano", resultado.getContrasena()));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el email ya está en uso por otro cliente")
    void crear_conEmailDuplicado_deberiaLanzarExcepcion() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.of(clientePrueba));

        Cliente nuevo = new Cliente(null, "Otro", "Cliente", "987654321", "carlos@mail.com", "otraClave", null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            clienteService.crear(nuevo);
        });

        assertTrue(ex.getMessage().contains("Ya existe un cliente"));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Debe retornar cliente cuando el ID existe")
    void obtenerPorId_cuandoExiste_deberiaRetornarCliente() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));

        Cliente resultado = clienteService.obtenerPorId(1);

        assertNotNull(resultado);
        assertEquals("Carlos", resultado.getNombre());
        verify(clienteRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el ID no existe")
    void obtenerPorId_cuandoNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            clienteService.obtenerPorId(99);
        });

        assertTrue(exception.getMessage().contains("no encontrado con id: 99"));
        verify(clienteRepository, times(1)).findById(99);
    }

    @Test
    @DisplayName("darDeBaja debe aplicar Soft Delete pasando el estado a INACTIVO")
    void darDeBaja_deberiaCambiarEstadoAInactivo() {
        clientePrueba.setEstado(EstadoCliente.ACTIVO);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clienteService.darDeBaja(1);

        assertEquals(EstadoCliente.INACTIVO, clientePrueba.getEstado());
        verify(clienteRepository, times(1)).save(clientePrueba);
    }

    @Test
    @DisplayName("registrarCredenciales debe completar email y contraseña (hasheada) y anular el código usado")
    void registrarCredenciales_conCodigoValido_deberiaCompletarPerfilYAnularCodigo() {
        clientePrueba.setCodigoActivacion("ABCD1234");
        when(clienteRepository.findByCodigoActivacion("ABCD1234")).thenReturn(Optional.of(clientePrueba));
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente resultado = clienteService.registrarCredenciales(
                "ABCD1234", "carlos@mail.com", "claveEnTextoPlano");

        assertEquals("carlos@mail.com", resultado.getEmail());
        assertNotEquals("claveEnTextoPlano", resultado.getContrasena());
        assertTrue(passwordEncoder.matches("claveEnTextoPlano", resultado.getContrasena()));
        assertNull(resultado.getCodigoActivacion());
    }

    @Test
    @DisplayName("registrarCredenciales debe lanzar excepción si el código de activación no existe")
    void registrarCredenciales_conCodigoInvalido_deberiaLanzarExcepcion() {
        when(clienteRepository.findByCodigoActivacion(any())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            clienteService.registrarCredenciales("NOEXISTE", "x@mail.com", "clave");
        });

        assertTrue(ex.getMessage().contains("Código de activación inválido"));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("registrarCredenciales debe lanzar excepción si el cliente ya tiene una cuenta")
    void registrarCredenciales_conCuentaExistente_deberiaLanzarExcepcion() {
        clientePrueba.setCodigoActivacion("ABCD1234");
        clientePrueba.setContrasena("$2a$10$yaHasheada");
        when(clienteRepository.findByCodigoActivacion("ABCD1234")).thenReturn(Optional.of(clientePrueba));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            clienteService.registrarCredenciales("ABCD1234", "otro@mail.com", "clave");
        });

        assertTrue(ex.getMessage().contains("Ya existe una cuenta"));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("autenticar con email y contraseña correctos debe retornar true")
    void autenticar_conCredencialesCorrectas_deberiaRetornarTrue() {
        clientePrueba.setEmail("carlos@mail.com");
        clientePrueba.setContrasena(passwordEncoder.encode("miClave123"));
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.of(clientePrueba));

        assertTrue(clienteService.autenticar("carlos@mail.com", "miClave123"));
    }

    @Test
    @DisplayName("autenticar con contraseña incorrecta debe retornar false")
    void autenticar_conContrasenaIncorrecta_deberiaRetornarFalse() {
        clientePrueba.setEmail("carlos@mail.com");
        clientePrueba.setContrasena(passwordEncoder.encode("miClave123"));
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.of(clientePrueba));

        assertFalse(clienteService.autenticar("carlos@mail.com", "claveIncorrecta"));
    }

    @Test
    @DisplayName("autenticar un cliente que aún no completó su registro debe retornar false")
    void autenticar_sinContrasenaCargada_deberiaRetornarFalse() {
        clientePrueba.setEmail("carlos@mail.com");
        clientePrueba.setContrasena(null);
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.of(clientePrueba));

        assertFalse(clienteService.autenticar("carlos@mail.com", "cualquierClave"));
    }

    @Test
    @DisplayName("obtenerRespuestaPorId debe exponer la fecha de vencimiento del último pago, no la de uno anterior")
    void obtenerRespuestaPorId_conVariosPagos_deberiaUsarFechaDelUltimoPago() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));

        // El método derivado (findTopByClienteIdOrderByFechaVencimientoDesc) ya trae, de
        // todos los pagos del cliente, el de mayor fecha de vencimiento: acá simulamos que
        // el cliente tiene un pago viejo y este es el más nuevo, para comprobar que el
        // service expone esta fecha y no la de un pago anterior.
        LocalDate fechaPagoAnterior = LocalDate.of(2025, 1, 15);
        LocalDate fechaUltimoPago = LocalDate.of(2026, 3, 20);
        Pago ultimoPago = new Pago();
        ultimoPago.setFechaVencimiento(fechaUltimoPago);
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.of(ultimoPago));

        ClienteResponse resultado = clienteService.obtenerRespuestaPorId(1);

        assertEquals(fechaUltimoPago, resultado.getFechaVencimiento());
        assertNotEquals(fechaPagoAnterior, resultado.getFechaVencimiento());
    }

    @Test
    @DisplayName("obtenerRespuestaPorId de un cliente sin pagos debe devolver fechaVencimiento null")
    void obtenerRespuestaPorId_sinPagos_deberiaDevolverFechaVencimientoNull() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.empty());

        ClienteResponse resultado = clienteService.obtenerRespuestaPorId(1);

        assertNull(resultado.getFechaVencimiento());
    }

    @Test
    @DisplayName("obtenerTodosConVencimiento no debe mezclar la fecha de vencimiento entre distintos clientes")
    void obtenerTodosConVencimiento_deberiaAsignarLaFechaCorrectaACadaCliente() {
        Cliente otroCliente = new Cliente(2, "Ana", "Lopez", "987654321", null, null, EstadoCliente.ACTIVO, null);
        when(clienteRepository.findAll()).thenReturn(List.of(clientePrueba, otroCliente));

        LocalDate fechaClientePrueba = LocalDate.of(2026, 5, 1);
        LocalDate fechaOtroCliente = LocalDate.of(2026, 8, 10);

        Pago pagoClientePrueba = new Pago();
        pagoClientePrueba.setCliente(clientePrueba);
        pagoClientePrueba.setFechaVencimiento(fechaClientePrueba);

        Pago pagoOtroCliente = new Pago();
        pagoOtroCliente.setCliente(otroCliente);
        pagoOtroCliente.setFechaVencimiento(fechaOtroCliente);

        when(pagoRepository.findUltimoPagoPorCadaCliente())
                .thenReturn(List.of(pagoClientePrueba, pagoOtroCliente));

        List<ClienteResponse> resultado = clienteService.obtenerTodosConVencimiento();

        ClienteResponse respuestaClientePrueba = resultado.stream()
                .filter(r -> r.getId().equals(1)).findFirst().orElseThrow();
        ClienteResponse respuestaOtroCliente = resultado.stream()
                .filter(r -> r.getId().equals(2)).findFirst().orElseThrow();

        assertEquals(fechaClientePrueba, respuestaClientePrueba.getFechaVencimiento());
        assertEquals(fechaOtroCliente, respuestaOtroCliente.getFechaVencimiento());
    }

    @Test
    @DisplayName("Un socio con dos pagos que vencen el mismo día no debe romper el listado")
    void obtenerTodosConVencimiento_conPagosEmpatadosEnLaMismaFecha_noDeberiaFallar() {
        // findUltimoPagoPorCadaCliente() devuelve todos los pagos empatados en la fecha
        // máxima, así que un socio que compró dos pases el mismo día aparece dos veces.
        // Sin función de merge en el toMap, el listado completo devolvía 500.
        when(clienteRepository.findAll()).thenReturn(List.of(clientePrueba));
        LocalDate mismaFecha = LocalDate.of(2026, 6, 15);

        Pago primerPago = new Pago();
        primerPago.setCliente(clientePrueba);
        primerPago.setFechaVencimiento(mismaFecha);

        Pago segundoPago = new Pago();
        segundoPago.setCliente(clientePrueba);
        segundoPago.setFechaVencimiento(mismaFecha);

        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(primerPago, segundoPago));

        List<ClienteResponse> resultado = clienteService.obtenerTodosConVencimiento();

        assertEquals(1, resultado.size());
        assertEquals(mismaFecha, resultado.getFirst().getFechaVencimiento());
    }
}

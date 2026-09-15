package com.gimnasio.api.services;

import com.gimnasio.api.dto.ClienteRequest;
import com.gimnasio.api.dto.ClienteResponse;
import com.gimnasio.api.dto.PaginaResponse;
import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

        ClienteRequest nuevo = new ClienteRequest("Carlos", "Gómez", "123456789", null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotNull(resultado);
        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Debe crear un cliente sin email ni contraseña (alta desde la app de escritorio)")
    void crear_sinCredenciales_deberiaGuardarClienteSinEmailNiContrasena() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClienteRequest nuevo = new ClienteRequest("Carlos", "Gómez", "123456789", null, null);
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

        ClienteRequest nuevo = new ClienteRequest("Carlos", "Gómez", "123456789", null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotNull(resultado.getCodigoActivacion());
    }

    @Test
    @DisplayName("Crear con credenciales directas no debe generar código de activación (no hace falta /registro)")
    void crear_conCredenciales_noDeberiaGenerarCodigoDeActivacion() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClienteRequest nuevo = new ClienteRequest("Carlos", "Gómez", "123456789", "carlos@mail.com", "clave123");
        Cliente resultado = clienteService.crear(nuevo);

        assertNull(resultado.getCodigoActivacion());
    }

    @Test
    @DisplayName("Crear debe ignorar el estado: ClienteRequest ni siquiera tiene ese campo, siempre queda INACTIVO")
    void crear_deberiaForzarInactivoSinImportarQueSePida() {
        // A diferencia de la versión vieja (que recibía la entidad Cliente completa y
        // había que anular "id"/"estado" a mano), ClienteRequest no tiene esos campos:
        // no hay forma de que el llamador los envíe. El caso "el body trae estado/id" se
        // prueba a nivel HTTP en ClienteControllerIntegrationTest, deserializando JSON
        // con esas claves de más contra el endpoint real.
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClienteRequest nuevo = new ClienteRequest("Carlos", "Gómez", "123456789", null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNull(resultado.getId());
        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
    }

    @Test
    @DisplayName("Debe hashear la contraseña cuando el cliente se registra con email y contraseña")
    void crear_conCredenciales_deberiaHashearLaContrasena() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClienteRequest nuevo = new ClienteRequest("Carlos", "Gómez", "123456789", "carlos@mail.com", "claveEnTextoPlano");
        Cliente resultado = clienteService.crear(nuevo);

        assertNotEquals("claveEnTextoPlano", resultado.getContrasena());
        assertTrue(passwordEncoder.matches("claveEnTextoPlano", resultado.getContrasena()));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el email ya está en uso por otro cliente")
    void crear_conEmailDuplicado_deberiaLanzarExcepcion() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.of(clientePrueba));

        ClienteRequest nuevo = new ClienteRequest("Otro", "Cliente", "987654321", "carlos@mail.com", "otraClave");

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

        RecursoNoEncontradoException exception = assertThrows(RecursoNoEncontradoException.class, () -> {
            clienteService.obtenerPorId(99);
        });

        assertTrue(exception.getMessage().contains("no encontrado con id: 99"));
        verify(clienteRepository, times(1)).findById(99);
    }

    @Test
    @DisplayName("actualizar debe devolver un ClienteResponse con los datos de contacto nuevos")
    void actualizar_deberiaDevolverClienteResponseActualizado() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.empty());

        ClienteRequest actualizacion = new ClienteRequest("Carlos Nuevo", "Gómez", "999999999", null, null);
        ClienteResponse resultado = clienteService.actualizar(1, actualizacion);

        assertEquals("Carlos Nuevo", resultado.getNombre());
        assertEquals("999999999", resultado.getTelefono());
    }

    @Test
    @DisplayName("cambiarEstado debe devolver un ClienteResponse con el estado nuevo")
    void cambiarEstado_deberiaDevolverClienteResponseConEstadoNuevo() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.empty());

        ClienteResponse resultado = clienteService.cambiarEstado(1, EstadoCliente.MOROSO);

        assertEquals(EstadoCliente.MOROSO, resultado.getEstado());
    }

    @Test
    @DisplayName("cambiarEstado no debe poder activar a un socio a mano: eso lo hace un pago")
    void cambiarEstado_conActivo_deberiaLanzarExcepcion() {
        // Sin esta regla, este endpoint esquiva las dos validaciones de dinero de la Fase 1
        // (monto menor al plan rechazado, y activación solo si el vencimiento es futuro).
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                clienteService.cambiarEstado(1, EstadoCliente.ACTIVO));

        assertTrue(ex.getMessage().contains("no se activa a mano"));
        verify(clienteRepository, never()).save(any(Cliente.class));
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
        // Con plan, como en la base: pagos.plan_id es NOT NULL. De paso sirve para
        // comprobar que el plan vigente del socio sale del último pago.
        ultimoPago.setPlan(new Plan(7, "Pase Mensual", 32500.0, 30));
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.of(ultimoPago));

        ClienteResponse resultado = clienteService.obtenerRespuestaPorId(1);

        assertEquals(fechaUltimoPago, resultado.getFechaVencimiento());
        assertNotEquals(fechaPagoAnterior, resultado.getFechaVencimiento());
        assertEquals("Pase Mensual", resultado.getPlanVigente().nombre());
        assertEquals(7, resultado.getPlanVigente().id());
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
        Pageable pageable = PageRequest.of(0, 20);
        when(clienteRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(clientePrueba, otroCliente), pageable, 2));

        LocalDate fechaClientePrueba = LocalDate.of(2026, 5, 1);
        LocalDate fechaOtroCliente = LocalDate.of(2026, 8, 10);

        Pago pagoClientePrueba = new Pago();
        pagoClientePrueba.setCliente(clientePrueba);
        pagoClientePrueba.setFechaVencimiento(fechaClientePrueba);
        pagoClientePrueba.setPlan(new Plan(7, "Pase Mensual", 32500.0, 30));

        Pago pagoOtroCliente = new Pago();
        pagoOtroCliente.setCliente(otroCliente);
        pagoOtroCliente.setFechaVencimiento(fechaOtroCliente);
        pagoOtroCliente.setPlan(new Plan(8, "Pase Diario", 1000.0, 1));

        when(pagoRepository.findUltimoPagoPorCadaCliente())
                .thenReturn(List.of(pagoClientePrueba, pagoOtroCliente));

        List<ClienteResponse> resultado = clienteService.obtenerTodosConVencimiento(pageable).contenido();

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
        Pageable pageable = PageRequest.of(0, 20);
        when(clienteRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(clientePrueba), pageable, 1));
        LocalDate mismaFecha = LocalDate.of(2026, 6, 15);

        Pago primerPago = new Pago();
        primerPago.setCliente(clientePrueba);
        primerPago.setFechaVencimiento(mismaFecha);
        primerPago.setPlan(new Plan(8, "Pase Diario", 1000.0, 1));

        Pago segundoPago = new Pago();
        segundoPago.setCliente(clientePrueba);
        segundoPago.setFechaVencimiento(mismaFecha);
        segundoPago.setPlan(new Plan(8, "Pase Diario", 1000.0, 1));

        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of(primerPago, segundoPago));

        PaginaResponse<ClienteResponse> resultado = clienteService.obtenerTodosConVencimiento(pageable);

        assertEquals(1, resultado.contenido().size());
        assertEquals(mismaFecha, resultado.contenido().getFirst().getFechaVencimiento());
    }

    @Test
    @DisplayName("obtenerTodosConVencimiento debe exponer la forma paginada (totalElementos, pagina, tamanio)")
    void obtenerTodosConVencimiento_deberiaExponerMetadatosDePaginacion() {
        Pageable pageable = PageRequest.of(0, 20);
        when(clienteRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(clientePrueba), pageable, 1));
        when(pagoRepository.findUltimoPagoPorCadaCliente()).thenReturn(List.of());

        PaginaResponse<ClienteResponse> resultado = clienteService.obtenerTodosConVencimiento(pageable);

        assertEquals(0, resultado.pagina());
        assertEquals(20, resultado.tamanio());
        assertEquals(1, resultado.totalElementos());
        assertEquals(1, resultado.totalPaginas());
    }

    @Test
    @DisplayName("buscarPorNombreConVencimiento debe devolver una lista vacía si no hay coincidencias, sin lanzar excepción")
    void buscarPorNombreConVencimiento_sinCoincidencias_deberiaDevolverListaVacia() {
        when(clienteRepository.findByNombreContainingIgnoreCase("Nadie")).thenReturn(List.of());

        List<ClienteResponse> resultado = clienteService.buscarPorNombreConVencimiento("Nadie");

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("buscarPorNombreConVencimiento con coincidencia debe devolver una lista con ese cliente")
    void buscarPorNombreConVencimiento_conCoincidencia_deberiaDevolverListaConElCliente() {
        when(clienteRepository.findByNombreContainingIgnoreCase("Carlos")).thenReturn(List.of(clientePrueba));
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.empty());

        List<ClienteResponse> resultado = clienteService.buscarPorNombreConVencimiento("Carlos");

        assertEquals(1, resultado.size());
        assertEquals("Carlos", resultado.getFirst().getNombre());
    }

    @Test
    @DisplayName("buscarPorNombreConVencimiento debe devolver todas las coincidencias parciales")
    void buscarPorNombreConVencimiento_conVariasCoincidencias_deberiaDevolverlasTodas() {
        // La búsqueda es por coincidencia parcial, así que un fragmento como "car" tiene
        // que traer a todos los socios que lo contengan, no exigir el nombre completo.
        Cliente otroCarlos = new Cliente(2, "Carla", "Gomez", "555", null, null, EstadoCliente.ACTIVO, null);
        when(clienteRepository.findByNombreContainingIgnoreCase("car"))
                .thenReturn(List.of(clientePrueba, otroCarlos));
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(1)).thenReturn(Optional.empty());
        when(pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(2)).thenReturn(Optional.empty());

        List<ClienteResponse> resultado = clienteService.buscarPorNombreConVencimiento("car");

        assertEquals(2, resultado.size());
    }
}

package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.services.impl.ClienteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    // Se usa una instancia real (no un mock) por el mismo motivo que en UsuarioServiceTest:
    // el hashing no es determinístico en su salida, mockearlo no aportaría nada.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ClienteServiceImpl clienteService;

    private Cliente clientePrueba;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteServiceImpl(clienteRepository, passwordEncoder);
        clientePrueba = new Cliente(1, "Carlos", "Gómez", "123456789", null, null, EstadoCliente.INACTIVO);
    }

    @Test
    @DisplayName("Debe crear un cliente con estado inicial INACTIVO")
    void crear_deberiaGuardarClienteConEstadoInactivo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotNull(resultado);
        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Debe crear un cliente sin email ni contraseña (alta desde la app de escritorio)")
    void crear_sinCredenciales_deberiaGuardarClienteSinEmailNiContrasena() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNull(resultado.getEmail());
        assertNull(resultado.getContrasena());
        verify(clienteRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("Crear debe ignorar cualquier id enviado en el body (no debe poder pisar otra fila)")
    void crear_conIdEnviado_deberiaIgnorarlo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente conIdAjeno = new Cliente(99, "Carlos", "Gómez", "123456789", null, null, null);
        Cliente resultado = clienteService.crear(conIdAjeno);

        assertNull(resultado.getId());
    }

    @Test
    @DisplayName("Crear debe forzar estado INACTIVO aunque el body envíe otro estado explícito")
    void crear_conEstadoExplicito_deberiaForzarInactivo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente conEstadoActivo = new Cliente(null, "Carlos", "Gómez", "123456789", null, null, EstadoCliente.ACTIVO);
        Cliente resultado = clienteService.crear(conEstadoActivo);

        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
    }

    @Test
    @DisplayName("Debe hashear la contraseña cuando el cliente se registra con email y contraseña")
    void crear_conCredenciales_deberiaHashearLaContrasena() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", "carlos@mail.com", "claveEnTextoPlano", null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotEquals("claveEnTextoPlano", resultado.getContrasena());
        assertTrue(passwordEncoder.matches("claveEnTextoPlano", resultado.getContrasena()));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el email ya está en uso por otro cliente")
    void crear_conEmailDuplicado_deberiaLanzarExcepcion() {
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.of(clientePrueba));

        Cliente nuevo = new Cliente(null, "Otro", "Cliente", "987654321", "carlos@mail.com", "otraClave", null);

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
    @DisplayName("registrarCredenciales debe completar email y contraseña (hasheada) de un cliente existente")
    void registrarCredenciales_conDatosCoincidentes_deberiaCompletarPerfil() {
        when(clienteRepository.findByNombreIgnoreCaseAndApellidoIgnoreCaseAndTelefono("Carlos", "Gómez", "123456789"))
                .thenReturn(Optional.of(clientePrueba));
        when(clienteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente resultado = clienteService.registrarCredenciales(
                "Carlos", "Gómez", "123456789", "carlos@mail.com", "claveEnTextoPlano");

        assertEquals("carlos@mail.com", resultado.getEmail());
        assertNotEquals("claveEnTextoPlano", resultado.getContrasena());
        assertTrue(passwordEncoder.matches("claveEnTextoPlano", resultado.getContrasena()));
    }

    @Test
    @DisplayName("registrarCredenciales debe lanzar excepción si no encuentra un cliente con esos datos")
    void registrarCredenciales_sinCoincidencia_deberiaLanzarExcepcion() {
        when(clienteRepository.findByNombreIgnoreCaseAndApellidoIgnoreCaseAndTelefono(any(), any(), any()))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            clienteService.registrarCredenciales("Nadie", "Desconocido", "000", "x@mail.com", "clave");
        });

        assertTrue(ex.getMessage().contains("no encontrado"));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("registrarCredenciales debe lanzar excepción si el cliente ya tiene una cuenta")
    void registrarCredenciales_conCuentaExistente_deberiaLanzarExcepcion() {
        clientePrueba.setContrasena("$2a$10$yaHasheada");
        when(clienteRepository.findByNombreIgnoreCaseAndApellidoIgnoreCaseAndTelefono("Carlos", "Gómez", "123456789"))
                .thenReturn(Optional.of(clientePrueba));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            clienteService.registrarCredenciales("Carlos", "Gómez", "123456789", "otro@mail.com", "clave");
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
}

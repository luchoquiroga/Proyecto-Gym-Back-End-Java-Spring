package com.gimnasio.api.services;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.services.impl.UsuarioServiceImpl;
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
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    // Se usa una instancia real (no un mock) porque el hashing es determinístico en su comportamiento
    // pero no en su salida (cada encode() genera un hash distinto), por lo que mockearlo no aportaría nada.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioServiceImpl usuarioService;

    private Usuario usuarioAdmin;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        usuarioAdmin = new Usuario(1, "admin", passwordEncoder.encode("admin123"), RolUsuario.ADMIN);
    }

    @Test
    @DisplayName("Autenticar con usuario y contraseña correctos debe retornar true")
    void autenticar_conCredencialesCorrectas_deberiaRetornarTrue() {
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));

        boolean resultado = usuarioService.autenticar("admin", "admin123");

        assertTrue(resultado);
        verify(usuarioRepository, times(1)).findByNombre("admin");
    }

    @Test
    @DisplayName("Autenticar con contraseña incorrecta debe retornar false")
    void autenticar_conContrasenaIncorrecta_deberiaRetornarFalse() {
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));

        boolean resultado = usuarioService.autenticar("admin", "clave_falsa");

        assertFalse(resultado);
    }

    @Test
    @DisplayName("Autenticar con usuario que no existe debe retornar false")
    void autenticar_conUsuarioInexistente_deberiaRetornarFalse() {
        when(usuarioRepository.findByNombre("no_existe")).thenReturn(Optional.empty());

        boolean resultado = usuarioService.autenticar("no_existe", "1234");

        assertFalse(resultado);
    }

    @Test
    @DisplayName("Registrar debe guardar la contraseña hasheada, nunca en texto plano")
    void registrar_deberiaHashearLaContrasena() {
        when(usuarioRepository.findByNombre("nuevo")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario nuevo = new Usuario(null, "nuevo", "claveEnTextoPlano", RolUsuario.GERENCIA);
        Usuario guardado = usuarioService.registrar(nuevo);

        assertNotEquals("claveEnTextoPlano", guardado.getContrasena());
        assertTrue(passwordEncoder.matches("claveEnTextoPlano", guardado.getContrasena()));
    }

    @Test
    @DisplayName("Registrar debe lanzar excepción si el nombre de usuario ya está en uso")
    void registrar_cuandoNombreYaExiste_deberiaLanzarExcepcion() {
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));

        Usuario duplicado = new Usuario(null, "admin", "otraClave", RolUsuario.GERENCIA);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.registrar(duplicado);
        });

        assertTrue(ex.getMessage().contains("Ya existe un usuario"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Actualizar contraseña debe guardar la nueva clave hasheada")
    void actualizarContrasena_deberiaCambiarClave() {
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario actualizado = usuarioService.actualizarContrasena("admin", "nuevaClave2026");

        assertNotEquals("nuevaClave2026", actualizado.getContrasena());
        assertTrue(passwordEncoder.matches("nuevaClave2026", actualizado.getContrasena()));
        verify(usuarioRepository, times(1)).save(usuarioAdmin);
    }

    @Test
    @DisplayName("Registrar debe ignorar cualquier id enviado en el body (no debe poder pisar otra fila)")
    void registrar_conIdEnviado_deberiaIgnorarlo() {
        when(usuarioRepository.findByNombre("nuevo")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario conIdAjeno = new Usuario(99, "nuevo", "clave123", RolUsuario.GERENCIA);
        Usuario guardado = usuarioService.registrar(conIdAjeno);

        assertNull(guardado.getId());
    }

    @Test
    @DisplayName("Eliminar debe rechazar que un usuario se elimine a sí mismo")
    void eliminar_conAutoEliminacion_deberiaLanzarExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.eliminar(1, 1));

        assertTrue(ex.getMessage().contains("propio usuario"));
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar debe rechazar borrar al último ADMIN del sistema")
    void eliminar_conUltimoAdmin_deberiaLanzarExcepcion() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.countByRol(RolUsuario.ADMIN)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.eliminar(1, 2));

        assertTrue(ex.getMessage().contains("último administrador"));
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar un ADMIN que no es el último ni quien lo pide debe funcionar normalmente")
    void eliminar_casoNormal_deberiaEliminar() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.countByRol(RolUsuario.ADMIN)).thenReturn(2L);

        usuarioService.eliminar(1, 2);

        verify(usuarioRepository, times(1)).deleteById(1);
    }
}

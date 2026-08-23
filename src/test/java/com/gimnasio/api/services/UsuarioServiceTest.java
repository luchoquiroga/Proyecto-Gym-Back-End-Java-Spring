package com.gimnasio.api.services;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.services.impl.UsuarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private Usuario usuarioAdmin;

    @BeforeEach
    void setUp() {
        usuarioAdmin = new Usuario(1, "admin", "admin123", RolUsuario.ADMIN);
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
    @DisplayName("Actualizar contraseña debe modificar la clave exitosamente")
    void actualizarContrasena_deberiaCambiarClave() {
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario actualizado = usuarioService.actualizarContrasena("admin", "nuevaClave2026");

        assertEquals("nuevaClave2026", actualizado.getContrasena());
        verify(usuarioRepository, times(1)).save(usuarioAdmin);
    }
}

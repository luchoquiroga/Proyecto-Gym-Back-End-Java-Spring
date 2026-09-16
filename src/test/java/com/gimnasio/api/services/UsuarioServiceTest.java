package com.gimnasio.api.services;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.security.RefreshTokenService;
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

    // Una baja o un cambio de contraseña tienen que cerrar las sesiones de la cuenta; acá
    // solo interesa que se pida, el comportamiento real se prueba en los tests de integración.
    @Mock
    private RefreshTokenService refreshTokenService;

    // Se usa una instancia real (no un mock) porque el hashing es determinístico en su comportamiento
    // pero no en su salida (cada encode() genera un hash distinto), por lo que mockearlo no aportaría nada.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioServiceImpl usuarioService;

    private Usuario usuarioAdmin;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioServiceImpl(usuarioRepository, passwordEncoder, refreshTokenService);
        usuarioAdmin = new Usuario(1, "admin", passwordEncoder.encode("admin123456789"), RolUsuario.ADMIN);
    }

    @Test
    @DisplayName("Autenticar con usuario y contraseña correctos debe retornar true")
    void autenticar_conCredencialesCorrectas_deberiaRetornarTrue() {
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));

        boolean resultado = usuarioService.autenticar("admin", "admin123456789");

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
    @DisplayName("Cambiar la contraseña propia debe guardar la nueva clave hasheada y cerrar las sesiones")
    void cambiarContrasenaPropia_deberiaCambiarClave() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario actualizado = usuarioService.cambiarContrasenaPropia(1, "admin123456789", "nuevaClave2026");

        assertNotEquals("nuevaClave2026", actualizado.getContrasena());
        assertTrue(passwordEncoder.matches("nuevaClave2026", actualizado.getContrasena()));
        verify(usuarioRepository, times(1)).save(usuarioAdmin);
        verify(refreshTokenService, times(1)).revocarTodosDe(1);
    }

    @Test
    @DisplayName("Cambiar la contraseña propia con la actual incorrecta no debe tocar nada")
    void cambiarContrasenaPropia_conActualIncorrecta_deberiaLanzarExcepcion() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.cambiarContrasenaPropia(1, "la_que_no_es", "nuevaClave2026"));

        assertTrue(ex.getMessage().contains("actual no es correcta"));
        assertTrue(passwordEncoder.matches("admin123456789", usuarioAdmin.getContrasena()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("La contraseña nueva no puede ser la misma que la actual")
    void cambiarContrasenaPropia_conLaMismaClave_deberiaLanzarExcepcion() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.cambiarContrasenaPropia(1, "admin123456789", "admin123456789"));

        assertTrue(ex.getMessage().contains("distinta de la actual"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("El reset administrativo no se puede usar contra la cuenta propia")
    void resetearContrasena_contraUnoMismo_deberiaLanzarExcepcion() {
        // Si se pudiera, un ADMIN se cambiaría su propia clave sin saber la anterior y la
        // verificación del endpoint propio no serviría de nada para el rol que más importa.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.resetearContrasena(1, "nuevaClave2026", 1));

        assertTrue(ex.getMessage().contains("/cambiar-contrasena"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
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
    @DisplayName("Dar de baja debe rechazar que un usuario se dé de baja a sí mismo")
    void darDeBaja_conAutoBaja_deberiaLanzarExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.cambiarActivo(1, false, 1));

        assertTrue(ex.getMessage().contains("propio usuario"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Dar de baja debe rechazar al último ADMIN activo del sistema")
    void darDeBaja_conUltimoAdminActivo_deberiaLanzarExcepcion() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.countByRolAndActivoTrue(RolUsuario.ADMIN)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.cambiarActivo(1, false, 2));

        assertTrue(ex.getMessage().contains("último administrador"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Un ADMIN ya dado de baja no cuenta como administrador disponible")
    void darDeBaja_conOtroAdminInactivo_deberiaLanzarExcepcion() {
        // El caso que el countByRol viejo dejaba pasar: dos filas ADMIN en la tabla, pero una
        // inactiva, así que dar de baja a la otra dejaba el sistema sin nadie que lo administre.
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.countByRolAndActivoTrue(RolUsuario.ADMIN)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> usuarioService.cambiarActivo(1, false, 2));

        assertTrue(usuarioAdmin.isActivo());
    }

    @Test
    @DisplayName("Dar de baja a un ADMIN que no es el último ni quien lo pide debe desactivarlo y cerrar sus sesiones")
    void darDeBaja_casoNormal_deberiaDesactivar() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.countByRolAndActivoTrue(RolUsuario.ADMIN)).thenReturn(2L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.cambiarActivo(1, false, 2);

        // La fila se conserva: si se borrara, `pagos.registrado_por` de todo lo que cobró se
        // iría a NULL (FK ON DELETE SET NULL de V3) y se perdería la auditoría de caja.
        assertFalse(usuarioAdmin.isActivo());
        verify(usuarioRepository, never()).deleteById(any());
        verify(usuarioRepository, times(1)).save(usuarioAdmin);
        verify(refreshTokenService, times(1)).revocarTodosDe(1);
    }

    @Test
    @DisplayName("Reactivar una cuenta dada de baja debe volver a habilitarla")
    void cambiarActivo_reactivando_deberiaHabilitarLaCuenta() {
        usuarioAdmin.setActivo(false);
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioAdmin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario reactivado = usuarioService.cambiarActivo(1, true, 2);

        assertTrue(reactivado.isActivo());
        // Reactivar no revoca nada: no hay motivo para cerrar sesiones que ya no existen.
        verify(refreshTokenService, never()).revocarTodosDe(any());
    }

    @Test
    @DisplayName("Autenticar una cuenta dada de baja debe fallar aunque la contraseña sea correcta")
    void autenticar_conCuentaInactiva_deberiaRetornarFalse() {
        usuarioAdmin.setActivo(false);
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));

        assertFalse(usuarioService.autenticar("admin", "admin123456789"));
    }

    @Test
    @DisplayName("Registrar con el nombre de una cuenta dada de baja debe explicar que hay que reactivarla")
    void registrar_conNombreDeCuentaDadaDeBaja_deberiaSugerirReactivar() {
        // El nombre sigue ocupado por la fila inactiva (UNIQUE desde V4), así que sin este
        // mensaje el alta fallaría con un 409 de la constraint, sin decir qué hacer.
        usuarioAdmin.setActivo(false);
        when(usuarioRepository.findByNombre("admin")).thenReturn(Optional.of(usuarioAdmin));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.registrar(new Usuario(null, "admin", "otraClave", RolUsuario.GERENCIA)));

        assertTrue(ex.getMessage().contains("dada de baja"));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
}

package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.CambioContrasenaRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.security.JwtService;
import com.gimnasio.api.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST versionado para autenticación y gestión de usuarios.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        boolean autenticado = usuarioService.autenticar(request.getNombre(), request.getContrasena());
        if (!autenticado) {
            Map<String, Object> error = new HashMap<>();
            error.put("mensaje", "Credenciales incorrectas");
            error.put("status", HttpStatus.UNAUTHORIZED.value());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        Usuario usuario = usuarioService.buscarPorNombre(request.getNombre());
        String token = jwtService.generarToken(usuario);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("mensaje", "Inicio de sesión exitoso");
        respuesta.put("token", token);
        respuesta.put("id", usuario.getId());
        respuesta.put("nombre", usuario.getNombre());
        respuesta.put("rol", usuario.getRol());

        return ResponseEntity.ok(respuesta);
    }

    @PostMapping
    public ResponseEntity<Usuario> registrar(@RequestBody Usuario usuario) {
        Usuario nuevoUsuario = usuarioService.registrar(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoUsuario);
    }

    @PutMapping("/cambiar-contrasena")
    public ResponseEntity<Usuario> cambiarContrasena(@RequestBody CambioContrasenaRequest request) {
        Usuario usuarioActualizado = usuarioService.actualizarContrasena(
                request.getNombre(),
                request.getNuevaContrasena()
        );
        return ResponseEntity.ok(usuarioActualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

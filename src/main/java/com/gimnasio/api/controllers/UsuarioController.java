package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.CambioContrasenaRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.dto.LoginResponse;
import com.gimnasio.api.dto.MensajeResponse;
import com.gimnasio.api.dto.RefreshResponse;
import com.gimnasio.api.dto.UsuarioResponse;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.security.JwtService;
import com.gimnasio.api.security.RefreshTokenService;
import com.gimnasio.api.services.UsuarioService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST versionado para autenticación y gestión de usuarios.
 * Implementa el esquema de doble token: access token de corta duración devuelto
 * en el body y refresh token de larga duración en una cookie HttpOnly.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private static final String NOMBRE_COOKIE_REFRESH = "refreshToken";

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:None}")
    private String cookieSameSite;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        boolean autenticado = usuarioService.autenticar(request.getNombre(), request.getContrasena());
        if (!autenticado) {
            Map<String, Object> error = new HashMap<>();
            error.put("mensaje", "Credenciales incorrectas");
            error.put("status", HttpStatus.UNAUTHORIZED.value());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        Usuario usuario = usuarioService.buscarPorNombre(request.getNombre());
        String accessToken = jwtService.generarAccessToken(usuario);
        String refreshToken = refreshTokenService.crear(usuario);

        agregarCookieRefresh(response, refreshToken, maxAgeRefreshSegundos());

        LoginResponse cuerpo = new LoginResponse(
                "Inicio de sesión exitoso",
                accessToken,
                UsuarioResponse.desde(usuario)
        );
        return ResponseEntity.ok(cuerpo);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = NOMBRE_COOKIE_REFRESH, required = false) String refreshToken,
                                      HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MensajeResponse("Refresh token expirado o inválido"));
        }

        try {
            Usuario usuario = refreshTokenService.validar(refreshToken);
            String nuevoRefreshToken = refreshTokenService.rotar(refreshToken, usuario);
            String nuevoAccessToken = jwtService.generarAccessToken(usuario);

            agregarCookieRefresh(response, nuevoRefreshToken, maxAgeRefreshSegundos());

            RefreshResponse cuerpo = new RefreshResponse(nuevoAccessToken, UsuarioResponse.desde(usuario));
            return ResponseEntity.ok(cuerpo);
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MensajeResponse("Refresh token expirado o inválido"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = NOMBRE_COOKIE_REFRESH, required = false) String refreshToken,
                                     HttpServletResponse response) {
        if (refreshToken != null) {
            refreshTokenService.revocar(refreshToken);
        }

        agregarCookieRefresh(response, "", 0);

        return ResponseEntity.ok(new MensajeResponse("Sesión cerrada correctamente"));
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

    private void agregarCookieRefresh(HttpServletResponse response, String valor, long maxAgeSegundos) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NOMBRE_COOKIE_REFRESH, valor)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(maxAgeSegundos);

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private long maxAgeRefreshSegundos() {
        return jwtService.getRefreshExpiracionMs() / 1000;
    }
}

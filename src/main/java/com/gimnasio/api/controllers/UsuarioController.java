package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.CambioActivoRequest;
import com.gimnasio.api.dto.CambioContrasenaRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.dto.LoginResponse;
import com.gimnasio.api.dto.MensajeResponse;
import com.gimnasio.api.dto.PaginaResponse;
import com.gimnasio.api.dto.RefreshResponse;
import com.gimnasio.api.dto.ResetContrasenaRequest;
import com.gimnasio.api.dto.UsuarioRequest;
import com.gimnasio.api.dto.UsuarioResponse;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.security.JwtService;
import com.gimnasio.api.security.RefreshTokenService;
import com.gimnasio.api.services.UsuarioService;
import com.gimnasio.api.security.AuthPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
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

    /**
     * Listado de cuentas de staff (solo ADMIN, ver SecurityConfig). Incluye las dadas de
     * baja: `activo` las distingue, y son las que hay que ver para poder reactivarlas.
     */
    @GetMapping
    public ResponseEntity<PaginaResponse<UsuarioResponse>> obtenerTodos(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PaginaResponse.desde(usuarioService.obtenerTodos(pageable), UsuarioResponse::desde));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody UsuarioRequest request) {
        // Sin id: el DTO no lo expone, así que no hay forma de pisar otra fila desde el body.
        Usuario usuario = new Usuario(null, request.getNombre(), request.getContrasena(), request.getRol());
        Usuario nuevoUsuario = usuarioService.registrar(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.desde(nuevoUsuario));
    }

    /**
     * Cambia la contraseña de la cuenta que hace la llamada. Quién es sale del token, no del
     * body: antes el body traía un `nombre`, así que el llamador elegía a quién cambiársela.
     */
    @PutMapping("/cambiar-contrasena")
    public ResponseEntity<UsuarioResponse> cambiarContrasena(@Valid @RequestBody CambioContrasenaRequest request,
                                                             @AuthenticationPrincipal AuthPrincipal principal) {
        Usuario usuarioActualizado = usuarioService.cambiarContrasenaPropia(
                principal.id(),
                request.getContrasenaActual(),
                request.getNuevaContrasena()
        );
        return ResponseEntity.ok(UsuarioResponse.desde(usuarioActualizado));
    }

    /**
     * Reset administrativo de la contraseña de otra cuenta (solo ADMIN, ver SecurityConfig).
     */
    @PutMapping("/{id}/contrasena")
    public ResponseEntity<UsuarioResponse> resetearContrasena(@PathVariable Integer id,
                                                              @Valid @RequestBody ResetContrasenaRequest request,
                                                              @AuthenticationPrincipal AuthPrincipal principal) {
        Usuario usuarioActualizado = usuarioService.resetearContrasena(
                id,
                request.getNuevaContrasena(),
                principal.id()
        );
        return ResponseEntity.ok(UsuarioResponse.desde(usuarioActualizado));
    }

    /**
     * Activa o desactiva una cuenta. Es la única forma de revertir una baja: el nombre de
     * login sigue ocupado por esa fila, así que no se puede recrear la cuenta.
     */
    @PatchMapping("/{id}/activo")
    public ResponseEntity<UsuarioResponse> cambiarActivo(@PathVariable Integer id,
                                                         @Valid @RequestBody CambioActivoRequest request,
                                                         @AuthenticationPrincipal AuthPrincipal principal) {
        Usuario usuarioActualizado = usuarioService.cambiarActivo(id, request.getActivo(), principal.id());
        return ResponseEntity.ok(UsuarioResponse.desde(usuarioActualizado));
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

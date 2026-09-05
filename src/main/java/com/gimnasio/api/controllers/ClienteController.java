package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteLoginResponse;
import com.gimnasio.api.dto.ClienteRefreshResponse;
import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.ClienteResponse;
import com.gimnasio.api.dto.MensajeResponse;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.security.AuthPrincipal;
import com.gimnasio.api.security.ClienteRefreshTokenService;
import com.gimnasio.api.security.JwtService;
import com.gimnasio.api.services.ClienteService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST versionado para la gestión de Clientes y, a partir de
 * /registro, /login, /refresh y /logout, para su autenticación en el portal web
 * (esquema de doble token igual al de UsuarioController, pero completamente
 * independiente: un Cliente no es un Usuario/staff, ver RolUsuario).
 */
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private static final String NOMBRE_COOKIE_REFRESH = "clienteRefreshToken";

    private final ClienteService clienteService;
    private final JwtService jwtService;
    private final ClienteRefreshTokenService clienteRefreshTokenService;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:None}")
    private String cookieSameSite;

    @GetMapping
    public ResponseEntity<List<Cliente>> obtenerTodos() {
        return ResponseEntity.ok(clienteService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> obtenerPorId(@PathVariable Integer id,
                                                 @AuthenticationPrincipal AuthPrincipal principal) {
        boolean esStaff = "ADMIN".equals(principal.rol()) || "GERENCIA".equals(principal.rol());
        if (!esStaff && !id.equals(principal.id())) {
            throw new AccessDeniedException("No podés acceder a datos de otro cliente");
        }
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<Cliente> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(clienteService.buscarPorNombre(nombre));
    }

    @PostMapping
    public ResponseEntity<Cliente> crear(@RequestBody Cliente cliente) {
        Cliente nuevoCliente = clienteService.crear(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoCliente);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> actualizar(@PathVariable Integer id, @RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteService.actualizar(id, cliente));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Cliente> cambiarEstado(
            @PathVariable Integer id,
            @RequestParam EstadoCliente nuevoEstado) {
        return ResponseEntity.ok(clienteService.cambiarEstado(id, nuevoEstado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Integer id) {
        clienteService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registro(@Valid @RequestBody ClienteRegistroRequest request) {
        clienteService.registrarCredenciales(
                request.getNombre(), request.getApellido(), request.getTelefono(),
                request.getEmail(), request.getContrasena());
        return ResponseEntity.ok(new MensajeResponse("Registro completado, ya podés iniciar sesión"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody ClienteLoginRequest request, HttpServletResponse response) {
        boolean autenticado = clienteService.autenticar(request.getEmail(), request.getContrasena());
        if (!autenticado) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MensajeResponse("Credenciales incorrectas"));
        }

        Cliente cliente = clienteService.buscarPorEmail(request.getEmail());
        String accessToken = jwtService.generarAccessTokenCliente(cliente);
        String refreshToken = clienteRefreshTokenService.crear(cliente);

        agregarCookieRefresh(response, refreshToken, maxAgeRefreshSegundos());

        ClienteLoginResponse cuerpo = new ClienteLoginResponse(
                "Inicio de sesión exitoso", accessToken, ClienteResponse.desde(cliente));
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
            Cliente cliente = clienteRefreshTokenService.validar(refreshToken);
            String nuevoRefreshToken = clienteRefreshTokenService.rotar(refreshToken, cliente);
            String nuevoAccessToken = jwtService.generarAccessTokenCliente(cliente);

            agregarCookieRefresh(response, nuevoRefreshToken, maxAgeRefreshSegundos());

            ClienteRefreshResponse cuerpo = new ClienteRefreshResponse(nuevoAccessToken, ClienteResponse.desde(cliente));
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
            clienteRefreshTokenService.revocar(refreshToken);
        }

        agregarCookieRefresh(response, "", 0);

        return ResponseEntity.ok(new MensajeResponse("Sesión cerrada correctamente"));
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

package com.gimnasio.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que se ejecuta en cada request: lee el JWT del header Authorization,
 * lo valida y, si es correcto, autentica al usuario para esa petición.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length());
        try {
            Claims claims = jwtService.validarYObtenerClaims(token);
            String nombreUsuario = claims.getSubject();
            String rol = claims.get("rol", String.class);
            Integer id = claims.get("id", Integer.class);

            // El principal es un AuthPrincipal (no un String) para que los controllers puedan
            // saber "a quién pertenece este token" sin volver a consultar la base. Nota: como
            // no es String/Principal, Authentication#getName() haría toString() sobre él si
            // alguna vez se usara (hoy no se usa en ningún lado del código).
            var principal = new AuthPrincipal(id, nombreUsuario, rol);
            var authoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
            var autenticacion = new UsernamePasswordAuthenticationToken(principal, null, authoridades);
            SecurityContextHolder.getContext().setAuthentication(autenticacion);

            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            responderNoAutorizado(response, "Token inválido o expirado");
        }
    }

    private void responderNoAutorizado(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"mensaje\":\"" + mensaje + "\"}"
        );
    }
}

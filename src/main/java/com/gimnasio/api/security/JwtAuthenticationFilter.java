package com.gimnasio.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    /**
     * Marca que deja el filtro en la request cuando el Bearer no sirve. El entry point de
     * SecurityConfig la lee para seguir respondiendo "Token inválido o expirado" en vez del
     * mensaje genérico de "iniciá sesión".
     */
    public static final String ATRIBUTO_TOKEN_INVALIDO = JwtAuthenticationFilter.class.getName() + ".tokenInvalido";

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

        // Un token que no sirve NO corta la request acá: sigue como anónima y es la regla de
        // SecurityConfig la que decide. Antes se respondía 401 en el acto, y eso rompía los
        // endpoints públicos que reciben un token viejo: la web manda el access token vencido
        // en el logout, el logout nunca llegaba al controller, y la cookie de refresh quedaba
        // viva 30 días en una PC compartida como la del mostrador.
        String token = header.substring("Bearer ".length());
        try {
            Claims claims = jwtService.validarYObtenerClaims(token);
            // Un refresh token bien firmado no sirve como access token: se trata como inválido.
            if (jwtService.esRefreshToken(token)) {
                throw new JwtException("Se usó un refresh token como access token");
            }

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
        } catch (JwtException e) {
            request.setAttribute(ATRIBUTO_TOKEN_INVALIDO, true);
        }
        // Fuera del try a propósito: una JwtException que saliera de más adentro de la cadena
        // no tiene que confundirse con un token inválido.
        filterChain.doFilter(request, response);
    }
}

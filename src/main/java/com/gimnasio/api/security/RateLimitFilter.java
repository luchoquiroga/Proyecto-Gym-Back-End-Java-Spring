package com.gimnasio.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos de login para frenar fuerza bruta: 5 intentos por minuto,
 * por IP, por endpoint de login. En memoria del proceso (sin Redis) — suficiente
 * para el tamaño y el deployment actual de esta app; no sobrevive un restart ni
 * escala a múltiples instancias, y no resuelve X-Forwarded-For (asume que no hay
 * un proxy inverso relevante todavía delante de la app).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_LIMITADAS = Set.of(
            "/api/v1/usuarios/login",
            "/api/v1/clientes/login"
    );

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacidad;

    public RateLimitFilter(@Value("${app.security.rate-limit.capacidad:5}") int capacidad) {
        this.capacidad = capacidad;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !RUTAS_LIMITADAS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clave = request.getRemoteAddr() + ":" + request.getRequestURI();
        Bucket bucket = buckets.computeIfAbsent(clave, k -> crearBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"mensaje\":\"Demasiados intentos, esperá un minuto\"}"
            );
        }
    }

    private Bucket crearBucket() {
        Bandwidth limite = Bandwidth.classic(capacidad, io.github.bucket4j.Refill.greedy(capacidad, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limite).build();
    }
}

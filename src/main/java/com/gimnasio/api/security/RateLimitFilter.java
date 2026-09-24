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

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos de login para frenar fuerza bruta: 5 intentos por minuto,
 * por IP, por endpoint de login. En memoria del proceso (sin Redis) — suficiente
 * para el tamaño y el deployment actual de esta app; no sobrevive un restart ni
 * escala a múltiples instancias.
 *
 * <p><b>De dónde sale la IP.</b> En producción la app corre detrás de proxies (el balanceador
 * de Render, y a futuro Vercel delante), así que {@code getRemoteAddr()} es la IP del último
 * proxy y no la del usuario: con ella, todos compartían un mismo cupo de 5 intentos. La IP real
 * viaja en {@code X-Forwarded-For}, donde cada proxy <i>agrega al final</i> la IP de quien le
 * habló. Las entradas de la izquierda las puede escribir cualquiera (basta mandar el header);
 * solo son confiables las que agregaron nuestros propios proxies, que son las de la derecha.
 * Por eso se cuenta desde la derecha: con {@code proxiesConfiables = N} se toma la entrada
 * N-ésima desde el final.
 *
 * <p><b>Limitación aceptada.</b> Si se configura más de un proxy (Render + Vercel) y alguien
 * llama directo a {@code *.onrender.com} salteando Vercel, la entrada que se toma la escribió
 * él, así que puede cambiar de cupo en cada intento. Para el tamaño de esta app se acepta:
 * el objetivo es que un socio que se equivoca no le bloquee el login al mostrador.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_LIMITADAS = Set.of(
            "/api/v1/usuarios/login",
            "/api/v1/clientes/login"
    );

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacidad;
    private final int proxiesConfiables;

    public RateLimitFilter(@Value("${app.security.rate-limit.capacidad:5}") int capacidad,
                           @Value("${app.security.rate-limit.proxies-confiables:1}") int proxiesConfiables) {
        this.capacidad = capacidad;
        this.proxiesConfiables = proxiesConfiables;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !RUTAS_LIMITADAS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clave = resolverIpCliente(request) + ":" + request.getRequestURI();
        Bucket bucket = buckets.computeIfAbsent(clave, k -> crearBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            // Sin charset explícito el contenedor escribe en ISO-8859-1 y los acentos se rompen.
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"mensaje\":\"Demasiados intentos, esperá un minuto\"}"
            );
        }
    }

    /**
     * Devuelve la IP del cliente según lo explicado en la documentación de la clase.
     * Sin el header (desarrollo local, tests) o sin proxies configurados, usa la conexión directa.
     */
    String resolverIpCliente(HttpServletRequest request) {
        String reenviadoPor = request.getHeader("X-Forwarded-For");
        if (proxiesConfiables <= 0 || reenviadoPor == null || reenviadoPor.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] ips = reenviadoPor.split(",");
        // Si la lista es más corta que la cadena de proxies, la entrada más vieja es la mejor
        // aproximación que hay (la agregó el primer proxy).
        int posicion = Math.max(0, ips.length - proxiesConfiables);
        return ips[posicion].trim();
    }

    private Bucket crearBucket() {
        Bandwidth limite = Bandwidth.classic(capacidad, io.github.bucket4j.Refill.greedy(capacidad, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limite).build();
    }
}

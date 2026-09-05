package com.gimnasio.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración central de seguridad: define qué endpoints son públicos, exige JWT
 * para el resto y establece el esquema de codificación de contraseñas.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Público
                        .requestMatchers("/ping").permitAll()
                        .requestMatchers("/api/v1/usuarios/login").permitAll()
                        .requestMatchers("/api/v1/usuarios/refresh").permitAll()
                        .requestMatchers("/api/v1/usuarios/logout").permitAll()
                        .requestMatchers("/api/v1/clientes/registro").permitAll()
                        .requestMatchers("/api/v1/clientes/login").permitAll()
                        .requestMatchers("/api/v1/clientes/refresh").permitAll()
                        .requestMatchers("/api/v1/clientes/logout").permitAll()
                        // Solo ADMIN: gestión de cuentas de usuario, catálogo de planes (altas/bajas) y dashboard
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/cambiar-contrasena").hasRole("ADMIN")
                        .requestMatchers("/api/v1/dashboard/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/planes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/planes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/planes/**").hasRole("ADMIN")
                        // ADMIN o GERENCIA: listados completos de clientes/pagos (un Cliente solo
                        // puede ver su propio registro, chequeo que hacen los controllers)
                        .requestMatchers(HttpMethod.GET, "/api/v1/clientes", "/api/v1/clientes/buscar")
                                .hasAnyRole("ADMIN", "GERENCIA")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pagos", "/api/v1/pagos/buscar")
                                .hasAnyRole("ADMIN", "GERENCIA")
                        // ADMIN o GERENCIA: operación diaria (clientes, pagos, consulta de planes)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":401,\"mensaje\":\"Es necesario iniciar sesión para acceder a este recurso\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":403,\"mensaje\":\"No tenés permisos para acceder a este recurso\"}"
                            );
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}

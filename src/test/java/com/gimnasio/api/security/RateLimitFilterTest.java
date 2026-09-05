package com.gimnasio.api.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test unitario y aislado del filtro (se instancia directo, no vía contexto de Spring),
 * a propósito: el bucket es un ConcurrentHashMap interno del filtro, y si se probara vía
 * MockMvc/login real compartiría estado (bean singleton) con el resto de la suite de
 * integración, que también hace logins reales — ver src/test/resources/application.properties
 * donde el límite queda deshabilitado (10000/min) para esa suite por este mismo motivo.
 */
class RateLimitFilterTest {

    @Test
    @DisplayName("Debe permitir hasta el límite configurado y bloquear con 429 a partir de ahí")
    void doFilterInternal_superadoElLimite_deberiaResponder429() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(5);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/usuarios/login");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filtro.doFilterInternal(request, response, chain);

            assertEquals(200, response.getStatus(), "intento " + (i + 1) + " debería pasar");
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/usuarios/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Demasiados intentos"));
        verify(chain, org.mockito.Mockito.times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("No debe limitar rutas que no son de login")
    void doFilterInternal_rutaNoLimitada_deberiaDejarPasarSiempre() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(1);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/clientes");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filtro.doFilterInternal(request, response, chain);

            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("IPs distintas no comparten el mismo cupo")
    void doFilterInternal_ipsDistintas_deberianTenerCuposIndependientes() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(1);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/clientes/login");
        req1.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filtro.doFilterInternal(req1, res1, chain);
        assertEquals(200, res1.getStatus());

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/clientes/login");
        req2.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filtro.doFilterInternal(req2, res2, chain);
        assertEquals(200, res2.getStatus());
    }
}

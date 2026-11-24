package com.gimnasio.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de punta a punta: levanta el servidor en un puerto real y le habla por HTTP, como
 * la web. A diferencia de los tests con MockMvc, acá viajan headers y cookies de verdad
 * (Set-Cookie, HttpOnly) y cada paso depende del anterior, igual que un día en el mostrador.
 *
 * <p>No es @Transactional (el servidor atiende en otro hilo y commitea de verdad), así que
 * lo que crea se borra a mano en {@link #limpiar()}, siempre por id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlujoCompletoE2ETest {

    @LocalServerPort
    private int puerto;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient http;
    private Integer planId;
    private Integer clienteId;

    @AfterEach
    void limpiar() {
        if (clienteId != null) {
            jdbcTemplate.update("DELETE FROM cliente_refresh_tokens WHERE cliente_id = ?", clienteId);
            jdbcTemplate.update("DELETE FROM pagos WHERE cliente_id = ?", clienteId);
            jdbcTemplate.update("DELETE FROM clientes WHERE id = ?", clienteId);
        }
        if (planId != null) {
            jdbcTemplate.update("DELETE FROM planes WHERE id = ?", planId);
        }
    }

    @Test
    @DisplayName("Alta de socio, registro, cobro, anulación y ciclo de sesión, todo por HTTP real")
    void unDiaEnElMostrador() throws Exception {
        // Los 4xx son parte del flujo (refresh viejo rechazado): no tienen que tirar excepción.
        http = RestClient.builder()
                .baseUrl("http://localhost:" + puerto)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();

        // 1. El admin entra al sistema.
        String tokenAdmin = json(enviar("POST", "/api/v1/usuarios/login", null,
                "{\"nombre\":\"admin\",\"contrasena\":\"admin123456789\"}", 200)).get("accessToken").asText();

        // 2. Crea un plan y da de alta un socio; el alta devuelve el código para la app.
        planId = json(enviar("POST", "/api/v1/planes", tokenAdmin,
                "{\"nombre\":\"Plan E2E\",\"precio\":20000,\"duracion\":30}", 201)).get("id").asInt();
        JsonNode alta = json(enviar("POST", "/api/v1/clientes", tokenAdmin,
                "{\"nombre\":\"Eva\",\"apellido\":\"Ende\",\"telefono\":\"555-E2E\",\"documento\":\"E2E" + System.nanoTime() % 1_000_000_000L + "\"}", 201));
        clienteId = alta.get("id").asInt();
        String codigo = alta.get("codigoActivacion").asText();
        assertEquals("INACTIVO", alta.get("estado").asText());

        // 3. El socio canjea el código y se loguea: el refresh viaja en una cookie HttpOnly.
        String email = "eva.e2e." + clienteId + "@test.com";
        enviar("POST", "/api/v1/clientes/registro", null,
                "{\"codigoActivacion\":\"" + codigo + "\",\"email\":\"" + email + "\",\"contrasena\":\"claveEva12345\"}", 200);
        ResponseEntity<String> login = enviar("POST", "/api/v1/clientes/login", null,
                "{\"email\":\"" + email + "\",\"contrasena\":\"claveEva12345\"}", 200);
        String tokenSocio = json(login).get("accessToken").asText();
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("HttpOnly"), "La cookie de refresh tiene que ser HttpOnly: " + setCookie);
        String cookieRefresh = valorCookie(setCookie);

        // 4. Sin pagos todavía, el socio se ve inactivo y sin vencimiento.
        JsonNode perfil = json(enviar("GET", "/api/v1/clientes/" + clienteId, tokenSocio, null, 200));
        assertEquals("INACTIVO", perfil.get("estado").asText());
        assertTrue(perfil.get("fechaVencimiento").isNull());

        // 5. Lo cobran en el mostrador y el socio ve su vencimiento.
        int pagoId = json(enviar("POST", "/api/v1/pagos", tokenAdmin,
                "{\"clienteId\":" + clienteId + ",\"planId\":" + planId + ",\"montoAbonado\":20000}", 201)).get("id").asInt();
        perfil = json(enviar("GET", "/api/v1/clientes/" + clienteId, tokenSocio, null, 200));
        assertEquals("ACTIVO", perfil.get("estado").asText());
        assertEquals(LocalDate.now().plusDays(30).toString(), perfil.get("fechaVencimiento").asText());

        // 6. El admin anula el cobro: el socio vuelve a quedar inactivo en el momento.
        enviar("POST", "/api/v1/pagos/" + pagoId + "/anulacion", tokenAdmin, "{\"motivo\":\"cobro duplicado\"}", 200);
        perfil = json(enviar("GET", "/api/v1/clientes/" + clienteId, tokenSocio, null, 200));
        assertEquals("INACTIVO", perfil.get("estado").asText());

        // 7. Refresh rota la cookie; la vieja deja de servir.
        ResponseEntity<String> refresh = enviarConCookie("/api/v1/clientes/refresh", cookieRefresh, 200);
        String cookieNueva = valorCookie(refresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertNotEquals(cookieRefresh, cookieNueva);
        enviarConCookie("/api/v1/clientes/refresh", cookieRefresh, 401);

        // 8. Logout revoca la cookie vigente.
        enviarConCookie("/api/v1/clientes/logout", cookieNueva, 200);
        enviarConCookie("/api/v1/clientes/refresh", cookieNueva, 401);
    }

    private ResponseEntity<String> enviar(String metodo, String ruta, String token, String cuerpo, int statusEsperado) {
        RestClient.RequestBodySpec pedido = http.method(org.springframework.http.HttpMethod.valueOf(metodo)).uri(ruta);
        if (token != null) {
            pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
        }
        ResponseEntity<String> respuesta = pedido.retrieve().toEntity(String.class);
        assertEquals(statusEsperado, respuesta.getStatusCode().value(), metodo + " " + ruta + " -> " + respuesta.getBody());
        return respuesta;
    }

    private ResponseEntity<String> enviarConCookie(String ruta, String cookie, int statusEsperado) {
        ResponseEntity<String> respuesta = http.post().uri(ruta)
                .header(HttpHeaders.COOKIE, "clienteRefreshToken=" + cookie)
                .retrieve().toEntity(String.class);
        assertEquals(statusEsperado, respuesta.getStatusCode().value(), "POST " + ruta + " -> " + respuesta.getBody());
        return respuesta;
    }

    /** "clienteRefreshToken=abc; Path=/; HttpOnly" -> "abc" */
    private String valorCookie(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private JsonNode json(ResponseEntity<String> respuesta) {
        return objectMapper.readTree(respuesta.getBody());
    }
}

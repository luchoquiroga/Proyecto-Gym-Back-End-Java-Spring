package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del flujo de autenticación de doble token para Usuario/staff
 * (login, refresh, logout) y de autorización por rol sobre un endpoint ADMIN-only,
 * corriendo contra la base real gimnasio_test. Cada test corre dentro de una
 * transacción que se revierte al final, por lo que no ensucia la base entre corridas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    @DisplayName("Login con admin/admin123 debe devolver access token, datos del usuario y cookie refreshToken HttpOnly")
    void login_conCredencialesCorrectas_deberiaDevolverTokensYCookie() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Inicio de sesión exitoso"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nombre").value("admin"))
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
                .andReturn();

        Cookie cookie = resultado.getResponse().getCookie("refreshToken");
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.getValue().isBlank());
    }

    @Test
    @DisplayName("Login con contraseña incorrecta debe devolver 401 con un mapa crudo, no un ErrorResponse")
    void login_conCredencialesIncorrectas_deberiaDevolver401ConMapaCrudo() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "claveIncorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales incorrectas"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    @DisplayName("Refresh con cookie válida rota los tokens, y la cookie vieja deja de servir")
    void refresh_conCookieValida_deberiaRotarYLuegoInvalidarLaVieja() throws Exception {
        MvcResult loginResult = login("admin", "admin123");
        Cookie cookieOriginal = loginResult.getResponse().getCookie("refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/usuarios/refresh").cookie(cookieOriginal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nombre").value("admin"))
                .andReturn();

        Cookie cookieNueva = refreshResult.getResponse().getCookie("refreshToken");

        // El refresh token siempre difiere (lleva jti único); el access token puede
        // coincidir si se emite dentro del mismo segundo de reloj, ya que sus claims
        // (id/subject/rol/iat/exp) no incluyen ningún nonce - no es un bug, así que
        // no se compara aquí, solo se valida la rotación real: la cookie.
        assertNotNull(cookieNueva);
        assertNotEquals(cookieOriginal.getValue(), cookieNueva.getValue());

        mockMvc.perform(post("/api/v1/usuarios/refresh").cookie(cookieOriginal))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Refresh token expirado o inválido"));
    }

    @Test
    @DisplayName("Refresh sin cookie debe devolver 401")
    void refresh_sinCookie_deberiaDevolver401() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Refresh token expirado o inválido"));
    }

    @Test
    @DisplayName("Logout revoca el refresh token, limpia la cookie, y deja el refresh posterior en 401")
    void logout_conCookieValida_deberiaRevocarYLimpiarCookie() throws Exception {
        MvcResult loginResult = login("admin", "admin123");
        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/usuarios/logout").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente"))
                .andReturn();

        Cookie cookieLimpia = logoutResult.getResponse().getCookie("refreshToken");
        assertNotNull(cookieLimpia);
        assertEquals(0, cookieLimpia.getMaxAge());

        mockMvc.perform(post("/api/v1/usuarios/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout sin cookie responde 200 igual (idempotente, no lanza excepción)")
    void logout_sinCookie_deberiaResponder200() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente"));
    }

    @Test
    @DisplayName("Crear usuario sin token debe devolver 401 del authenticationEntryPoint")
    void crearUsuario_sinToken_deberiaDevolver401() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"nuevo\",\"contrasena\":\"clave123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("Es necesario iniciar sesión para acceder a este recurso"));
    }

    @Test
    @DisplayName("Crear usuario con token de Cliente (rol distinto a ADMIN) debe devolver 403")
    void crearUsuario_conTokenDeCliente_deberiaDevolver403() throws Exception {
        String tokenCliente = registrarYLoguearCliente(
                "Pedro", "Suarez", "555-INT-1", "pedro.int1@test.com", "clavePedro123");

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"otro\",\"contrasena\":\"clave123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensaje").value("No tenés permisos para acceder a este recurso"));
    }

    @Test
    @DisplayName("Crear usuario con token ADMIN válido debe devolver 201")
    void crearUsuario_conTokenAdmin_deberiaDevolver201() throws Exception {
        MvcResult loginResult = login("admin", "admin123");
        String tokenAdmin = extraerCampo(loginResult, "accessToken");

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"nuevoStaff\",\"contrasena\":\"clave123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("nuevoStaff"))
                .andExpect(jsonPath("$.rol").value("GERENCIA"));
    }

    private MvcResult login(String nombre, String contrasena) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(nombre, contrasena))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extraerCampo(MvcResult resultado, String campo) throws Exception {
        JsonNode json = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return json.get(campo).asText();
    }

    private String registrarYLoguearCliente(String nombre, String apellido, String telefono,
                                             String email, String contrasena) throws Exception {
        clienteRepository.save(new Cliente(null, nombre, apellido, telefono, null, null, EstadoCliente.INACTIVO));

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteRegistroRequest(nombre, apellido, telefono, email, contrasena))))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClienteLoginRequest(email, contrasena))))
                .andExpect(status().isOk())
                .andReturn();

        return extraerCampo(loginResult, "accessToken");
    }
}

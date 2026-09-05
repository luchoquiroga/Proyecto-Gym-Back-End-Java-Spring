package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteRegistroRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del flujo de auto-registro y autenticación de doble token
 * para Cliente (registro, login, refresh, logout), corriendo contra la base real
 * gimnasio_test. Cada test corre dentro de una transacción que se revierte al
 * final, por lo que no ensucia la base entre corridas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClienteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Registro con datos que no matchean ningún cliente debe devolver 404")
    void registro_sinClienteCoincidente_deberiaDevolver404() throws Exception {
        ClienteRegistroRequest request = new ClienteRegistroRequest(
                "Nadie", "Desconocido", "000-NOPE", "nadie@test.com", "clave123");

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value(
                        "Cliente no encontrado con esos datos. Acercate al gimnasio para verificar tu registro."));
    }

    @Test
    @DisplayName("Registro con email mal formado debe devolver 400 por validación, no llegar al service")
    void registro_conEmailMalFormado_deberiaDevolver400() throws Exception {
        ClienteRegistroRequest request = new ClienteRegistroRequest(
                "Cualquiera", "Persona", "555-X", "esto-no-es-un-email", "clave123");

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores.email").isNotEmpty());
    }

    @Test
    @DisplayName("Registro exitoso completa email y hashea la contraseña, con distinto casing en nombre/apellido")
    void registro_conDatosCoincidentes_deberiaCompletarPerfil() throws Exception {
        Cliente cliente = clienteRepository.save(
                new Cliente(null, "Laura", "Fernandez", "555-C2", null, null, EstadoCliente.INACTIVO));

        ClienteRegistroRequest request = new ClienteRegistroRequest(
                "LAURA", "fernandez", "555-C2", "laura@test.com", "claveLaura123");

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Registro completado, ya podés iniciar sesión"));

        Cliente actualizado = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertEquals("laura@test.com", actualizado.getEmail());
        assertNotEquals("claveLaura123", actualizado.getContrasena());
        assertTrue(passwordEncoder.matches("claveLaura123", actualizado.getContrasena()));
    }

    @Test
    @DisplayName("Registro sobre un cliente ya reclamado debe devolver 400")
    void registro_clienteYaReclamado_deberiaDevolver400() throws Exception {
        clienteRepository.save(new Cliente(null, "Marta", "Diaz", "555-C3",
                "marta@test.com", passwordEncoder.encode("claveVieja"), EstadoCliente.INACTIVO));

        ClienteRegistroRequest request = new ClienteRegistroRequest(
                "Marta", "Diaz", "555-C3", "otro@test.com", "claveNueva");

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "Ya existe una cuenta registrada para este cliente. Iniciá sesión."));
    }

    @Test
    @DisplayName("Registro con email ya usado por otro cliente debe devolver 400")
    void registro_conEmailDuplicado_deberiaDevolver400() throws Exception {
        clienteRepository.save(new Cliente(null, "Existente", "Usuario", "555-C4",
                "ocupado@test.com", passwordEncoder.encode("clave"), EstadoCliente.ACTIVO));
        clienteRepository.save(
                new Cliente(null, "Nuevo", "Cliente", "555-C5", null, null, EstadoCliente.INACTIVO));

        ClienteRegistroRequest request = new ClienteRegistroRequest(
                "Nuevo", "Cliente", "555-C5", "ocupado@test.com", "claveNueva");

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "Ya existe un cliente registrado con el email: ocupado@test.com"));
    }

    @Test
    @DisplayName("Login exitoso devuelve access token, datos del cliente y cookie clienteRefreshToken HttpOnly")
    void login_conCredencialesCorrectas_deberiaDevolverTokensYCookie() throws Exception {
        registrarCliente("Sofia", "Ramirez", "555-C6", "sofia@test.com", "claveSofia123");

        MvcResult resultado = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("sofia@test.com", "claveSofia123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Inicio de sesión exitoso"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.cliente.email").value("sofia@test.com"))
                .andReturn();

        Cookie cookie = resultado.getResponse().getCookie("clienteRefreshToken");
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    @DisplayName("Login con credenciales incorrectas devuelve 401 con MensajeResponse (a diferencia del Map crudo de UsuarioController)")
    void login_conCredencialesIncorrectas_deberiaDevolver401ConMensajeResponse() throws Exception {
        registrarCliente("Bruno", "Lopez", "555-C7", "bruno@test.com", "claveBruno123");

        mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("bruno@test.com", "claveIncorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales incorrectas"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    @DisplayName("Refresh con cookie válida rota los tokens, y la cookie vieja deja de servir")
    void refresh_conCookieValida_deberiaRotarYLuegoInvalidarLaVieja() throws Exception {
        registrarCliente("Carla", "Nunez", "555-C8", "carla@test.com", "claveCarla123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("carla@test.com", "claveCarla123"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookieOriginal = loginResult.getResponse().getCookie("clienteRefreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/clientes/refresh").cookie(cookieOriginal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie cookieNueva = refreshResult.getResponse().getCookie("clienteRefreshToken");

        // El refresh token siempre difiere (lleva jti único); el access token puede
        // coincidir si se emite dentro del mismo segundo de reloj, ya que sus claims
        // (id/subject/rol/iat/exp) no incluyen ningún nonce - no es un bug, así que
        // no se compara aquí, solo se valida la rotación real: la cookie.
        assertNotEquals(cookieOriginal.getValue(), cookieNueva.getValue());

        mockMvc.perform(post("/api/v1/clientes/refresh").cookie(cookieOriginal))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Refresh token expirado o inválido"));
    }

    @Test
    @DisplayName("Logout revoca el refresh token, limpia la cookie, y deja el refresh posterior en 401")
    void logout_deberiaRevocarYLimpiarCookie() throws Exception {
        registrarCliente("Diego", "Molina", "555-C9", "diego@test.com", "claveDiego123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("diego@test.com", "claveDiego123"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = loginResult.getResponse().getCookie("clienteRefreshToken");

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/clientes/logout").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente"))
                .andReturn();
        Cookie cookieLimpia = logoutResult.getResponse().getCookie("clienteRefreshToken");
        assertNotNull(cookieLimpia);
        assertEquals(0, cookieLimpia.getMaxAge());

        mockMvc.perform(post("/api/v1/clientes/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token de Cliente no puede listar todos los clientes (listado completo es solo staff)")
    void listarClientes_conTokenDeCliente_deberiaDevolver403() throws Exception {
        registrarCliente("Emilia", "Torres", "555-C10", "emilia@test.com", "claveEmilia123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("emilia@test.com", "claveEmilia123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = extraerCampo(loginResult, "accessToken");

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token de Cliente puede ver su propio registro por id")
    void obtenerPorId_conTokenDeClientePropio_deberiaDevolver200() throws Exception {
        registrarCliente("Nico", "Vega", "555-C11", "nico@test.com", "claveNico123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("nico@test.com", "claveNico123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = extraerCampo(loginResult, "accessToken");
        String propioId = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("cliente").get("id").asText();

        mockMvc.perform(get("/api/v1/clientes/" + propioId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Integer.parseInt(propioId)));
    }

    @Test
    @DisplayName("Un token de Cliente no puede ver el registro de otro cliente por id")
    void obtenerPorId_conTokenDeOtroCliente_deberiaDevolver403() throws Exception {
        Cliente otroCliente = clienteRepository.save(
                new Cliente(null, "Ajeno", "Perez", "555-C12", null, null, EstadoCliente.INACTIVO));
        registrarCliente("Nico", "Vega", "555-C13", "nico2@test.com", "claveNico123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteLoginRequest("nico2@test.com", "claveNico123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = extraerCampo(loginResult, "accessToken");

        mockMvc.perform(get("/api/v1/clientes/" + otroCliente.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private void registrarCliente(String nombre, String apellido, String telefono,
                                   String email, String contrasena) throws Exception {
        clienteRepository.save(new Cliente(null, nombre, apellido, telefono, null, null, EstadoCliente.INACTIVO));

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteRegistroRequest(nombre, apellido, telefono, email, contrasena))))
                .andExpect(status().isOk());
    }

    private String extraerCampo(MvcResult resultado, String campo) throws Exception {
        JsonNode json = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return json.get(campo).asText();
    }
}

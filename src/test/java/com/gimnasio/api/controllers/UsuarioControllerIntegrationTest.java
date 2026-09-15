package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
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

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Login con admin/admin123 debe devolver access token, datos del usuario y cookie refreshToken HttpOnly")
    void login_conCredencialesCorrectas_deberiaDevolverTokensYCookie() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789"))))
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
    @DisplayName("Login con nombre en blanco debe devolver 400 por validación, no llegar al service")
    void login_conNombreEnBlanco_deberiaDevolver400() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", "admin123456789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores.nombre").isNotEmpty());
    }

    @Test
    @DisplayName("Refresh con cookie válida rota los tokens, y la cookie vieja deja de servir")
    void refresh_conCookieValida_deberiaRotarYLuegoInvalidarLaVieja() throws Exception {
        MvcResult loginResult = login("admin", "admin123456789");
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
        MvcResult loginResult = login("admin", "admin123456789");
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
    @DisplayName("Crear usuario con token ADMIN válido debe devolver 201 y nunca la contraseña")
    void crearUsuario_conTokenAdmin_deberiaDevolver201() throws Exception {
        MvcResult loginResult = login("admin", "admin123456789");
        String tokenAdmin = extraerCampo(loginResult, "accessToken");

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"nuevoStaff\",\"contrasena\":\"clave123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("nuevoStaff"))
                .andExpect(jsonPath("$.rol").value("GERENCIA"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    @DisplayName("Crear usuario enviando un id en el body debe ignorarlo (no pisa otra fila)")
    void crearUsuario_conIdEnviado_deberiaIgnorarlo() throws Exception {
        MvcResult loginResult = login("admin", "admin123456789");
        String tokenAdmin = extraerCampo(loginResult, "accessToken");

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9999,\"nombre\":\"otroStaff\",\"contrasena\":\"clave123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(9999)))
                .andExpect(jsonPath("$.nombre").value("otroStaff"));
    }

    @Test
    @DisplayName("Dar de baja a un usuario que tiene sesiones abiertas no debe fallar con 409")
    void darDeBaja_conRefreshTokensEnLaBase_deberiaFuncionar() throws Exception {
        // El caso que fallaba: las filas de refresh_tokens de un empleado que se logueó alguna
        // vez no se borran nunca (revocar solo las marca) y la FK no tiene cascada, así que el
        // DELETE moría con una violación de integridad. Ahora la baja es lógica y la fila queda.
        String tokenAdmin = tokenDeAdmin();
        Integer idStaff = crearStaff(tokenAdmin, "staffConSesion", "claveStaff123", "GERENCIA");

        MvcResult loginStaff = login("staffConSesion", "claveStaff123");
        Cookie cookieStaff = loginStaff.getResponse().getCookie("refreshToken");

        mockMvc.perform(delete("/api/v1/usuarios/" + idStaff)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        assertFalse(usuarioRepository.findById(idStaff).orElseThrow().isActivo());

        // La cuenta deja de servir: no puede loguearse de nuevo ni estirar la sesión que tenía.
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("staffConSesion", "claveStaff123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/usuarios/refresh").cookie(cookieStaff))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reactivar una cuenta dada de baja debe devolverle el acceso")
    void reactivar_cuentaDadaDeBaja_deberiaPoderLoguearseDeNuevo() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        Integer idStaff = crearStaff(tokenAdmin, "staffAReactivar", "claveStaff123", "GERENCIA");

        mockMvc.perform(delete("/api/v1/usuarios/" + idStaff)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/usuarios/" + idStaff + "/activo")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.contrasena").doesNotExist());

        login("staffAReactivar", "claveStaff123");
    }

    @Test
    @DisplayName("GERENCIA puede cambiar su propia contraseña y loguearse con la nueva")
    void cambiarContrasena_propia_deberiaFuncionarParaGerencia() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        crearStaff(tokenAdmin, "gerenteClave", "claveVieja123", "GERENCIA");
        String tokenGerencia = extraerCampo(login("gerenteClave", "claveVieja123"), "accessToken");

        mockMvc.perform(put("/api/v1/usuarios/cambiar-contrasena")
                        .header("Authorization", "Bearer " + tokenGerencia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"claveVieja123\",\"nuevaContrasena\":\"claveNueva456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("gerenteClave"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());

        login("gerenteClave", "claveNueva456");
    }

    @Test
    @DisplayName("Cambiar la contraseña con la actual equivocada devuelve 400 y no cambia nada")
    void cambiarContrasena_conActualIncorrecta_deberiaDevolver400() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        crearStaff(tokenAdmin, "gerenteTerco", "claveVieja123", "GERENCIA");
        String tokenGerencia = extraerCampo(login("gerenteTerco", "claveVieja123"), "accessToken");

        // 400 y no 401: la sesión es válida, lo que está mal es un dato del body.
        mockMvc.perform(put("/api/v1/usuarios/cambiar-contrasena")
                        .header("Authorization", "Bearer " + tokenGerencia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"la_que_no_es\",\"nuevaContrasena\":\"claveNueva456\"}"))
                .andExpect(status().isBadRequest());

        login("gerenteTerco", "claveVieja123");
    }

    @Test
    @DisplayName("GERENCIA no puede resetear la contraseña de otra cuenta")
    void resetearContrasena_conTokenGerencia_deberiaDevolver403() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        crearStaff(tokenAdmin, "gerenteCurioso", "claveGerente123", "GERENCIA");
        Integer idVictima = crearStaff(tokenAdmin, "otroStaffVictima", "claveVictima123", "GERENCIA");
        String tokenGerencia = extraerCampo(login("gerenteCurioso", "claveGerente123"), "accessToken");

        mockMvc.perform(put("/api/v1/usuarios/" + idVictima + "/contrasena")
                        .header("Authorization", "Bearer " + tokenGerencia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaContrasena\":\"meQuedoLaCuenta1\"}"))
                .andExpect(status().isForbidden());

        login("otroStaffVictima", "claveVictima123");
    }

    @Test
    @DisplayName("Un token de CLIENTE no puede cambiar contraseñas de staff aunque el id coincida")
    void cambiarContrasena_conTokenDeCliente_deberiaDevolver403() throws Exception {
        // Sin la regla de rol en SecurityConfig, este endpoint resolvería la cuenta con
        // principal.id(), que para un CLIENTE es un id de la tabla `clientes`: le cambiaría la
        // contraseña al empleado que tuviera el mismo número.
        String tokenCliente = registrarYLoguearCliente(
                "Ana", "Gomez", "555-INT-9", "ana.int9@test.com", "claveAna12345");

        mockMvc.perform(put("/api/v1/usuarios/cambiar-contrasena")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"claveAna12345\",\"nuevaContrasena\":\"claveNueva456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un ADMIN no puede resetearse la contraseña a sí mismo esquivando la actual")
    void resetearContrasena_contraUnoMismo_deberiaDevolver400() throws Exception {
        MvcResult loginResult = login("admin", "admin123456789");
        String tokenAdmin = extraerCampo(loginResult, "accessToken");
        Integer idAdmin = usuarioRepository.findByNombre("admin").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/usuarios/" + idAdmin + "/contrasena")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaContrasena\":\"claveNueva456\"}"))
                .andExpect(status().isBadRequest());

        login("admin", "admin123456789");
    }

    @Test
    @DisplayName("GET /usuarios devuelve la página de cuentas de staff, sin contraseñas")
    void listarUsuarios_conTokenAdmin_deberiaDevolverPaginaSinContrasenas() throws Exception {
        String tokenAdmin = tokenDeAdmin();

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.totalElementos").exists())
                .andExpect(jsonPath("$.contenido[*].contrasena").doesNotExist());
    }

    @Test
    @DisplayName("El listado de staff incluye las cuentas dadas de baja, que son las que hay que reactivar")
    void listarUsuarios_deberiaIncluirLasDadasDeBaja() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        Integer idStaff = crearStaff(tokenAdmin, "staffListado", "claveStaff123", "GERENCIA");

        mockMvc.perform(delete("/api/v1/usuarios/" + idStaff)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[?(@.nombre == 'staffListado')].activo").value(false));
    }

    @Test
    @DisplayName("GERENCIA no puede listar las cuentas de staff")
    void listarUsuarios_conTokenGerencia_deberiaDevolver403() throws Exception {
        String tokenAdmin = tokenDeAdmin();
        crearStaff(tokenAdmin, "gerenteMiron", "claveGerente123", "GERENCIA");
        String tokenGerencia = extraerCampo(login("gerenteMiron", "claveGerente123"), "accessToken");

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tokenGerencia))
                .andExpect(status().isForbidden());
    }

    private String tokenDeAdmin() throws Exception {
        return extraerCampo(login("admin", "admin123456789"), "accessToken");
    }

    /** Crea una cuenta de staff vía API y devuelve su id. */
    private Integer crearStaff(String tokenAdmin, String nombre, String contrasena, String rol) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"contrasena\":\"" + contrasena
                                + "\",\"rol\":\"" + rol + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return Integer.valueOf(extraerCampo(resultado, "id"));
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
        Cliente cliente = clienteRepository.save(
                new Cliente(null, nombre, apellido, telefono, null, null, EstadoCliente.INACTIVO, "CODIGOTEST"));

        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteRegistroRequest(cliente.getCodigoActivacion(), email, contrasena))))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClienteLoginRequest(email, contrasena))))
                .andExpect(status().isOk())
                .andReturn();

        return extraerCampo(loginResult, "accessToken");
    }
}

package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Casos borde encontrados en la revisión previa al pase a producción (2026-09-25). Cada test
 * fija un caso que antes terminaba en un 500, en un 409 engañoso, o, el más grave, en una
 * sesión que no se cerraba al hacer logout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CasosBordeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private Clock clock;

    @Value("${jwt.secret}")
    private String secretoJwt;

    // --- Logout con el access token vencido -------------------------------------------------

    @Test
    @DisplayName("Logout de staff con el access token vencido igual revoca el refresh y borra la cookie")
    void logoutStaff_conAccessTokenVencido_igualCierraLaSesion() throws Exception {
        // Regresión: el filtro JWT cortaba con 401 ante un Bearer vencido aunque el endpoint
        // fuera público, así que el logout nunca llegaba al controller. En una PC compartida
        // (el mostrador) la cookie de refresh seguía viva 30 días y el próximo que abriera la
        // web entraba con la sesión del anterior. La web manda el token viejo en el logout.
        MvcResult login = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookieRefresh = login.getResponse().getCookie("refreshToken");
        Usuario admin = usuarioRepository.findByNombre("admin").orElseThrow();

        mockMvc.perform(post("/api/v1/usuarios/logout")
                        .header("Authorization", "Bearer " + tokenVencidoDe(admin))
                        .cookie(cookieRefresh))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));

        mockMvc.perform(post("/api/v1/usuarios/refresh").cookie(cookieRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un access token vencido contra un endpoint protegido sigue dando 401")
    void endpointProtegido_conAccessTokenVencido_deberiaDevolver401() throws Exception {
        Usuario admin = usuarioRepository.findByNombre("admin").orElseThrow();

        mockMvc.perform(get("/api/v1/planes").header("Authorization", "Bearer " + tokenVencidoDe(admin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Token inválido o expirado"));
    }

    @Test
    @DisplayName("Un refresh token usado como Bearer contra un endpoint protegido da 401")
    void endpointProtegido_conRefreshTokenComoBearer_deberiaDevolver401() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789"))))
                .andReturn();
        String refresh = login.getResponse().getCookie("refreshToken").getValue();

        mockMvc.perform(get("/api/v1/planes").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    // --- Errores de quien llama que caían en el 500 genérico --------------------------------

    @Test
    @DisplayName("Un JSON mal formado devuelve 400, no 500")
    void jsonMalFormado_deberiaDevolver400() throws Exception {
        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\": 1, "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la solicitud es inválido o está mal formado"));
    }

    @Test
    @DisplayName("Un rol inexistente en el body devuelve 400, no 500")
    void rolInexistente_deberiaDevolver400() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x.borde\",\"contrasena\":\"claveLarga123\",\"rol\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una fecha imposible en el body devuelve 400, no 500")
    void fechaImposible_deberiaDevolver400() throws Exception {
        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"planId\":1,\"fechaPago\":\"2026-02-30\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Falta un parámetro obligatorio: 400 nombrando el parámetro")
    void parametroObligatorioFaltante_deberiaDevolver400() throws Exception {
        mockMvc.perform(patch("/api/v1/clientes/1/estado")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el parámetro obligatorio 'nuevoEstado'"));
    }

    @Test
    @DisplayName("Un método HTTP que la ruta no admite devuelve 405, no 500")
    void metodoNoSoportado_deberiaDevolver405() throws Exception {
        mockMvc.perform(delete("/api/v1/pagos")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Una ruta que no existe devuelve 404, no 500")
    void rutaInexistente_deberiaDevolver404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un body con Content-Type que no es JSON devuelve 415, no 500")
    void contentTypeNoSoportado_deberiaDevolver415() throws Exception {
        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // --- Datos que la base rechazaba con un 409 engañoso ------------------------------------

    @Test
    @DisplayName("Dos socios dados de alta con el email vacío no chocan entre sí")
    void altaDeDosSocios_conEmailVacio_noChocan() throws Exception {
        // Regresión: el "" se guardaba tal cual y el UNIQUE de email lo trataba como un valor
        // más, así que el segundo socio sin email rebotaba con "Ya existe un registro".
        String token = loguearComoAdmin();
        altaSocio(token, "{\"nombre\":\"Uno\",\"apellido\":\"Borde\",\"documento\":\"90000001\",\"email\":\"\"}")
                .andExpect(status().isCreated());
        altaSocio(token, "{\"nombre\":\"Dos\",\"apellido\":\"Borde\",\"documento\":\"90000002\",\"email\":\"\"}")
                .andExpect(status().isCreated());

        assertNull(clienteRepository.findByDocumento("90000002").orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Un nombre más largo que la columna devuelve 400 por validación, no 409")
    void nombreDemasiadoLargo_deberiaDevolver400() throws Exception {
        String nombreLargo = "a".repeat(101);
        altaSocio(loguearComoAdmin(),
                "{\"nombre\":\"" + nombreLargo + "\",\"apellido\":\"Borde\",\"documento\":\"90000003\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").isNotEmpty());
    }

    @Test
    @DisplayName("El email se guarda en minúsculas y el login no distingue mayúsculas")
    void email_seNormalizaEnAltaYLogin() throws Exception {
        altaSocio(loguearComoAdmin(),
                "{\"nombre\":\"Mayus\",\"apellido\":\"Borde\",\"documento\":\"90000004\","
                        + "\"email\":\"Socio.Borde@Test.com\",\"contrasena\":\"claveSocio123\"}")
                .andExpect(status().isCreated());

        assertEquals("socio.borde@test.com",
                clienteRepository.findByDocumento("90000004").orElseThrow().getEmail());

        mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"SOCIO.borde@test.com\",\"contrasena\":\"claveSocio123\"}"))
                .andExpect(status().isOk());
    }

    // --- Registro del socio en el portal ----------------------------------------------------

    @Test
    @DisplayName("El socio puede registrarse con el mismo email que el staff le cargó en el alta")
    void registro_conElEmailQueYaTeniaCargado_deberiaFuncionar() throws Exception {
        // Regresión: el chequeo de email repetido encontraba la fila del propio socio y
        // rechazaba el registro con "Ya existe un cliente registrado con ese email".
        clienteRepository.save(new Cliente(null, "Pre", "Cargado", null, "90000005",
                "precargado@test.com", null, EstadoCliente.INACTIVO, "PRE00001"));

        registrar("PRE00001", "precargado@test.com").andExpect(status().isOk());
    }

    @Test
    @DisplayName("El registro no puede usar el email de OTRO socio")
    void registro_conEmailDeOtroSocio_deberiaDevolver400() throws Exception {
        clienteRepository.save(new Cliente(null, "Otro", "Socio", null, "90000006",
                "ajeno@test.com", null, EstadoCliente.INACTIVO, null));
        clienteRepository.save(new Cliente(null, "Pre", "Registro", null, "90000007",
                null, null, EstadoCliente.INACTIVO, "PRE00002"));

        registrar("PRE00002", "AJENO@test.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya existe un cliente registrado con el email: ajeno@test.com"));
    }

    @Test
    @DisplayName("El código de activación se acepta en minúsculas y con espacios alrededor")
    void registro_conCodigoEnMinusculas_deberiaFuncionar() throws Exception {
        clienteRepository.save(new Cliente(null, "Codigo", "Minus", null, "90000008",
                null, null, EstadoCliente.INACTIVO, "PRE00003"));

        registrar(" pre00003 ", "minus@test.com").andExpect(status().isOk());
    }

    // --- Pagos ------------------------------------------------------------------------------

    @Test
    @DisplayName("Un pago con fecha de cobro futura se rechaza")
    void pago_conFechaFutura_deberiaDevolver400() throws Exception {
        Cliente socio = socioSinPagos("90000009");
        Plan plan = planRepository.findAll().get(0);

        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + socio.getId() + ",\"planId\":" + plan.getId()
                                + ",\"fechaPago\":\"" + LocalDate.now(clock).plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha de cobro no puede ser posterior a hoy."));
    }

    @Test
    @DisplayName("Un monto que desborda a infinito se rechaza por validación")
    void pago_conMontoInfinito_deberiaDevolver400() throws Exception {
        Cliente socio = socioSinPagos("90000010");
        Plan plan = planRepository.findAll().get(0);

        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + socio.getId() + ",\"planId\":" + plan.getId()
                                + ",\"montoAbonado\":1e400}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.montoAbonado").isNotEmpty());
    }

    @Test
    @DisplayName("Un plan con una duración absurda se rechaza por validación")
    void plan_conDuracionAbsurda_deberiaDevolver400() throws Exception {
        mockMvc.perform(post("/api/v1/planes")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Eterno\",\"precio\":100,\"duracion\":2000000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.duracion").isNotEmpty());
    }

    // --- Búsqueda y contraseñas -------------------------------------------------------------

    @Test
    @DisplayName("Buscar con el nombre vacío devuelve 400 en vez de traer a todos los socios")
    void buscar_conNombreVacio_deberiaDevolver400() throws Exception {
        mockMvc.perform(get("/api/v1/clientes/buscar").param("nombre", "  ")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una contraseña de más de 72 caracteres se rechaza con 400 en el alta de staff")
    void altaStaff_conContrasenaDeMasDe72_deberiaDevolver400() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x.larga\",\"contrasena\":\"" + "a".repeat(73)
                                + "\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").isNotEmpty());
    }

    @Test
    @DisplayName("Un login con una contraseña enorme responde 401, no 500")
    void login_conContrasenaEnorme_deberiaDevolver401() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "a".repeat(5000)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El nombre de staff se guarda sin espacios alrededor")
    void altaStaff_conEspaciosAlrededor_seGuardaRecortado() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  x.espacios  \",\"contrasena\":\"claveLarga123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("x.espacios"));
    }

    // --- Helpers ----------------------------------------------------------------------------

    private String tokenVencidoDe(Usuario usuario) {
        return new JwtService(secretoJwt, -60_000, 60_000).generarAccessToken(usuario);
    }

    private org.springframework.test.web.servlet.ResultActions altaSocio(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/clientes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private org.springframework.test.web.servlet.ResultActions registrar(String codigo, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/clientes/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClienteRegistroRequest(codigo, email, "claveSocio123"))));
    }

    private Cliente socioSinPagos(String documento) {
        return clienteRepository.save(
                new Cliente(null, "Sin", "Pagos", null, documento, null, null, EstadoCliente.INACTIVO, null));
    }

    private String loguearComoAdmin() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asText();
    }
}

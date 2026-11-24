package com.gimnasio.api.security;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests adversariales: cada uno es un intento concreto de un atacante (o de un empleado
 * dado de baja) de saltarse la autenticación, la autorización o la validación. Lo que se
 * fija no es solo "no entra", sino que nunca se responda un 500: un 500 delata que el
 * input llegó más lejos de lo que debía.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AtaquesIntegrationTest {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    // --- JWT forjados ---

    @Test
    @DisplayName("Un JWT con alg=none (sin firma) que dice ser ADMIN se rechaza")
    void jwtSinFirma_deberiaDevolver401() throws Exception {
        String cabecera = base64("{\"alg\":\"none\"}");
        String cuerpo = base64("{\"sub\":\"admin\",\"id\":" + idAdmin() + ",\"rol\":\"ADMIN\",\"exp\":" + enUnaHora() + "}");

        pedirListadoDeUsuarios(cabecera + "." + cuerpo + ".").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un JWT bien formado pero firmado con otra clave se rechaza")
    void jwtFirmadoConOtraClave_deberiaDevolver401() throws Exception {
        byte[] otraClave = new byte[32];
        new SecureRandom().nextBytes(otraClave);
        String token = Jwts.builder().subject("admin").claim("id", idAdmin()).claim("rol", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(otraClave)).compact();

        pedirListadoDeUsuarios(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cambiar el rol a ADMIN en el payload de un token real rompe la firma")
    void jwtConRolAdulterado_deberiaDevolver401() throws Exception {
        String[] partes = loguearComoGerencia("gerencia.ataque1").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("\"GERENCIA\"", "\"ADMIN\"");

        pedirListadoDeUsuarios(partes[0] + "." + base64(payload) + "." + partes[2]).andExpect(status().isUnauthorized());
    }

    // --- Cuenta de staff dada de baja ---

    @Test
    @DisplayName("Al dar de baja a un empleado, su access token deja de servir en el acto")
    void tokenDeStaffDadoDeBaja_dejaDeServirEnElActo() throws Exception {
        String tokenGerencia = loguearComoGerencia("gerencia.ataque2");
        Integer idGerencia = usuarioRepository.findByNombre("gerencia.ataque2").orElseThrow().getId();
        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenGerencia))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/usuarios/" + idGerencia + "/activo")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());

        // Antes de la baja en el filtro, este mismo token seguía cobrando hasta 30 minutos más.
        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenGerencia))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/pagos").header("Authorization", "Bearer " + tokenGerencia)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":1,\"planId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reactivar a un empleado no resucita los tokens que tenía antes de la baja")
    void reactivarStaff_noResucitaTokensViejos() throws Exception {
        String tokenViejo = loguearComoGerencia("gerencia.ataque3");
        Integer idGerencia = usuarioRepository.findByNombre("gerencia.ataque3").orElseThrow().getId();
        String tokenAdmin = loguearComoAdmin();

        cambiarActivo(tokenAdmin, idGerencia, false);
        cambiarActivo(tokenAdmin, idGerencia, true);

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenViejo))
                .andExpect(status().isUnauthorized());

        // `iat` va en segundos y el filtro descarta también lo emitido en el segundo de la baja.
        Thread.sleep(1100);
        String tokenNuevo = tokenDe(loginStaff("gerencia.ataque3", "claveGerencia123"));
        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenNuevo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Si el admin resetea la contraseña de un empleado, el token que ya tenía muere en el acto")
    void resetDeContrasena_mataElTokenRobado() throws Exception {
        String tokenRobado = loguearComoGerencia("gerencia.ataque4");
        Integer idGerencia = usuarioRepository.findByNombre("gerencia.ataque4").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/usuarios/" + idGerencia + "/contrasena")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaContrasena\":\"claveNueva12345\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenRobado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cambiar la propia contraseña cierra todas las sesiones, incluida la actual")
    void cambioDeContrasenaPropia_cierraLasSesiones() throws Exception {
        String token = loguearComoGerencia("gerencia.ataque5");

        mockMvc.perform(put("/api/v1/usuarios/cambiar-contrasena")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"claveGerencia123\",\"nuevaContrasena\":\"claveNueva12345\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        Thread.sleep(1100); // ver reactivarStaff_noResucitaTokensViejos
        String tokenNuevo = tokenDe(loginStaff("gerencia.ataque5", "claveNueva12345"));
        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenNuevo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un token sin fecha de emisión (iat) no sirve, aunque esté bien firmado")
    void tokenDeStaffSinIat_deberiaDevolver401() throws Exception {
        byte[] clave = entorno.getRequiredProperty("jwt.secret").getBytes();
        String token = Jwts.builder().subject("admin").claim("id", idAdmin()).claim("rol", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(clave)).compact();

        pedirListadoDeUsuarios(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token de staff cuyo id no existe en la base se rechaza")
    void tokenDeStaffInexistente_deberiaDevolver401() throws Exception {
        // Firmado con la clave real (como si se hubiera filtrado), pero de una cuenta que no existe.
        pedirListadoDeUsuarios(tokenFirmadoDeVerdad(999_999, "fantasma", "ADMIN")).andExpect(status().isUnauthorized());
    }

    // --- Refresh tokens cruzados entre portales ---

    @Test
    @DisplayName("La cookie de refresh de un socio no sirve en el refresh del staff, ni al revés")
    void refreshCruzadoEntrePortales_deberiaDevolver401() throws Exception {
        String refreshSocio = cookieDe(loguearClienteCompleto("555-AT1", "socio.ataque1@test.com"), "clienteRefreshToken");
        String refreshAdmin = cookieDe(mockMvc.perform(post("/api/v1/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789"))))
                .andExpect(status().isOk()).andReturn().getResponse(), "refreshToken");

        mockMvc.perform(post("/api/v1/usuarios/refresh").cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshSocio)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clientes/refresh").cookie(new jakarta.servlet.http.Cookie("clienteRefreshToken", refreshAdmin)))
                .andExpect(status().isUnauthorized());
    }

    // --- Enumeración de usuarios ---

    @Test
    @DisplayName("Login de staff: usuario inexistente y contraseña mala responden exactamente igual")
    void loginStaff_noPermiteEnumerarUsuarios() throws Exception {
        MockHttpServletResponse inexistente = loginStaff("no.existe.nunca", "cualquierClave1");
        MockHttpServletResponse claveMala = loginStaff("admin", "claveIncorrecta1");

        assertEquals(401, inexistente.getStatus());
        assertEquals(inexistente.getStatus(), claveMala.getStatus());
        assertEquals(inexistente.getContentAsString(), claveMala.getContentAsString());
    }

    @Test
    @DisplayName("Login de socio: email inexistente y contraseña mala responden exactamente igual")
    void loginCliente_noPermiteEnumerarEmails() throws Exception {
        loguearClienteCompleto("555-AT2", "socio.ataque2@test.com");
        MockHttpServletResponse inexistente = loginCliente("nadie.ataque@test.com", "cualquierClave1");
        MockHttpServletResponse claveMala = loginCliente("socio.ataque2@test.com", "claveIncorrecta1");

        assertEquals(401, inexistente.getStatus());
        assertEquals(inexistente.getStatus(), claveMala.getStatus());
        assertEquals(inexistente.getContentAsString(), claveMala.getContentAsString());
    }

    // --- Inyección ---

    @Test
    @DisplayName("Una inyección SQL en la búsqueda por nombre se trata como texto literal")
    void buscar_conInyeccionSql_noDevuelveTodosLosSocios() throws Exception {
        clienteRepository.save(new Cliente(null, "Ines", "Yeccion", "555-AT3", "555AT3", null, null, EstadoCliente.ACTIVO, null));

        for (String ataque : new String[]{"' OR '1'='1", "%' OR 1=1 --", "'; DROP TABLE clientes; --", "%", "_"}) {
            mockMvc.perform(get("/api/v1/clientes/buscar").param("nombre", ataque)
                            .header("Authorization", "Bearer " + loguearComoAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // --- Rutas escritas con trucos ---

    @Test
    @DisplayName("Rutas con .., %2e%2e, // o ; no permiten a un socio llegar a endpoints de staff")
    void rutasConTrucos_noSaltanLaAutorizacion() throws Exception {
        String tokenSocio = tokenDe(loguearClienteCompleto("555-AT4", "socio.ataque4@test.com"));
        for (String ruta : new String[]{
                "/api/v1/planes/../usuarios",
                "/api/v1/planes/%2e%2e/usuarios",
                "/api/v1//usuarios",
                "/api/v1/usuarios;jsessionid=x",
                "/api/v1/usuarios/",
                "/API/V1/USUARIOS"}) {
            int estado = mockMvc.perform(get(new URI(ruta)).header("Authorization", "Bearer " + tokenSocio))
                    .andReturn().getResponse().getStatus();
            assertTrue(estado >= 400 && estado < 500, ruta + " respondió " + estado);
        }
    }

    // --- Números absurdos ---

    @Test
    @DisplayName("Ids que desbordan un Integer y páginas absurdas dan 4xx o se acotan, nunca 500")
    void numerosAbsurdos_nuncaDevuelven500() throws Exception {
        String tokenAdmin = loguearComoAdmin();

        mockMvc.perform(get("/api/v1/clientes/99999999999").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/clientes/-1").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/clientes").param("size", "1000000").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanio").value(lessThanOrEqualTo(2000)));
        int paginaNegativa = mockMvc.perform(get("/api/v1/clientes").param("page", "-5")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andReturn().getResponse().getStatus();
        assertTrue(paginaNegativa < 500, "page=-5 respondió " + paginaNegativa);
        mockMvc.perform(post("/api/v1/pagos").header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":999999,\"planId\":999999}"))
                .andExpect(status().isNotFound());
    }

    // --- Mass assignment ---

    @Test
    @DisplayName("Mandar estado, id o rol en el registro no activa al socio ni le da otro rol")
    void registro_conCamposDeMas_losIgnora() throws Exception {
        Cliente cliente = clienteRepository.save(
                new Cliente(null, "Masa", "Asignada", "555-AT5", "555AT5", null, null, EstadoCliente.INACTIVO, "555-AT5"));

        mockMvc.perform(post("/api/v1/clientes/registro").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoActivacion\":\"555-AT5\",\"email\":\"masa@test.com\",\"contrasena\":\"claveMasa123\","
                        + "\"estado\":\"ACTIVO\",\"id\":1,\"rol\":\"ADMIN\"}"));

        Cliente despues = clienteRepository.findById(cliente.getId()).orElseThrow();
        assertEquals(EstadoCliente.INACTIVO, despues.getEstado());
        String token = tokenDe(loginCliente("masa@test.com", "claveMasa123"));
        pedirListadoDeUsuarios(token).andExpect(status().isForbidden());
    }

    // --- CORS y cookies ---

    @Test
    @DisplayName("Un preflight desde un origen desconocido no recibe permiso de CORS")
    void cors_origenDesconocido_noRecibePermiso() throws Exception {
        mockMvc.perform(options("/api/v1/usuarios/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(options("/api/v1/usuarios/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    @DisplayName("Las cookies de refresh salen HttpOnly, con SameSite y Path=/")
    void cookiesDeRefresh_tienenLosFlagsDeSeguridad() throws Exception {
        String staff = loginStaff("admin", "admin123456789").getHeader(HttpHeaders.SET_COOKIE);
        String socio = loguearClienteCompleto("555-AT6", "socio.ataque6@test.com").getHeader(HttpHeaders.SET_COOKIE);
        for (String setCookie : new String[]{staff, socio}) {
            assertNotNull(setCookie);
            org.hamcrest.MatcherAssert.assertThat(setCookie, containsString("HttpOnly"));
            org.hamcrest.MatcherAssert.assertThat(setCookie, containsString("SameSite=Lax"));
            org.hamcrest.MatcherAssert.assertThat(setCookie, containsString("Path=/"));
        }
    }

    // --- Helpers ---

    private org.springframework.test.web.servlet.ResultActions pedirListadoDeUsuarios(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token));
    }

    @Autowired
    private org.springframework.core.env.Environment entorno;

    private String tokenFirmadoDeVerdad(Integer id, String sub, String rol) {
        byte[] clave = entorno.getRequiredProperty("jwt.secret").getBytes(); // igual que JwtService
        return Jwts.builder().subject(sub).claim("id", id).claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(clave)).compact();
    }

    private void cambiarActivo(String tokenAdmin, Integer id, boolean activo) throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/" + id + "/activo")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":" + activo + "}"))
                .andExpect(status().isOk());
    }

    private Integer idAdmin() {
        return usuarioRepository.findByNombre("admin").orElseThrow().getId();
    }

    private long enUnaHora() {
        return System.currentTimeMillis() / 1000 + 3600;
    }

    private String base64(String texto) {
        return BASE64URL.encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletResponse loginStaff(String nombre, String contrasena) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(nombre, contrasena))))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse loginCliente(String email, String contrasena) throws Exception {
        return mockMvc.perform(post("/api/v1/clientes/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClienteLoginRequest(email, contrasena))))
                .andReturn().getResponse();
    }

    private String loguearComoAdmin() throws Exception {
        return tokenDe(loginStaff("admin", "admin123456789"));
    }

    private String loguearComoGerencia(String nombre) throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"contrasena\":\"claveGerencia123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated());
        return tokenDe(loginStaff(nombre, "claveGerencia123"));
    }

    /** Alta de socio con código, registro y login. Devuelve la respuesta del login. */
    private MockHttpServletResponse loguearClienteCompleto(String codigo, String email) throws Exception {
        clienteRepository.save(new Cliente(null, "Socio", "Ataque", codigo, codigo.replace("-", ""), null, null,
                EstadoCliente.ACTIVO, codigo));
        mockMvc.perform(post("/api/v1/clientes/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClienteRegistroRequest(codigo, email, "claveSocio123"))))
                .andExpect(status().isOk());
        MockHttpServletResponse login = loginCliente(email, "claveSocio123");
        assertEquals(200, login.getStatus());
        return login;
    }

    private String tokenDe(MockHttpServletResponse login) throws Exception {
        assertEquals(200, login.getStatus(), login.getContentAsString());
        return objectMapper.readTree(login.getContentAsString()).get("accessToken").asText();
    }

    private String cookieDe(MockHttpServletResponse respuesta, String nombre) {
        jakarta.servlet.http.Cookie cookie = respuesta.getCookie(nombre);
        assertNotNull(cookie, "No vino la cookie " + nombre);
        return cookie.getValue();
    }
}

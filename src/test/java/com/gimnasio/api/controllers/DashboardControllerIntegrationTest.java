package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de /api/v1/dashboard. Todo el dashboard es de ADMIN (regla de ruta
 * en SecurityConfig), incluido el conteo de socios aunque no sea un dato monetario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private PlanRepository planRepository;

    @Test
    @DisplayName("ADMIN recibe el conteo de socios por estado, que refleja los socios de la base")
    void contarSocios_conTokenAdmin_deberiaDevolverElConteo() throws Exception {
        long activosAntes = clienteRepository.countByEstado(EstadoCliente.ACTIVO);
        clienteRepository.save(new Cliente(null, "Pia", "Luna", "555-D10", "555D10", null, null, EstadoCliente.ACTIVO, null));

        mockMvc.perform(get("/api/v1/dashboard/socios")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activos").value(activosAntes + 1))
                .andExpect(jsonPath("$.morosos").isNumber())
                .andExpect(jsonPath("$.inactivos").isNumber());
    }

    @Test
    @DisplayName("GERENCIA no accede al conteo de socios: el dashboard entero es de ADMIN")
    void contarSocios_conTokenGerencia_deberiaDevolver403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/socios")
                        .header("Authorization", "Bearer " + loguearComoGerencia()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un CLIENTE no accede al conteo de socios")
    void contarSocios_conTokenDeCliente_deberiaDevolver403() throws Exception {
        clienteRepository.save(new Cliente(null, "Ciro", "Mena", "555-D11", "555D11", null, null, EstadoCliente.ACTIVO, "555-D11"));
        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteRegistroRequest("555-D11", "ciro@test.com", "claveCiro123"))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClienteLoginRequest("ciro@test.com", "claveCiro123"))))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/dashboard/socios")
                        .header("Authorization", "Bearer " + extraerToken(login)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El 401 de la cadena de seguridad declara UTF-8, para que los acentos no se rompan")
    void sinToken_deberiaResponder401EnUtf8() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/socios"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(jsonPath("$.mensaje").value("Es necesario iniciar sesión para acceder a este recurso"));
    }

    // Los tests de la serie usan meses de 2001: la base de test persiste entre corridas y
    // tiene cobros de "hoy", asi que un mes tan viejo es el unico que se puede afirmar vacio.

    @Test
    @DisplayName("La serie trae todos los meses del rango, tambien los que no tuvieron cobros")
    void gananciasPorMes_conMesSinCobros_deberiaTraerloEnCero() throws Exception {
        Plan plan = planRepository.findAll().get(0);
        registrarPago(plan, LocalDate.of(2001, 1, 15), false);
        registrarPago(plan, LocalDate.of(2001, 3, 10), false);

        mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .param("desde", "2001-01").param("hasta", "2001-03")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].anio").value(2001))
                .andExpect(jsonPath("$[0].mes").value(1))
                .andExpect(jsonPath("$[0].cantidadPagos").value(1))
                .andExpect(jsonPath("$[1].mes").value(2))
                .andExpect(jsonPath("$[1].totalGanancias").value(0.0))
                .andExpect(jsonPath("$[1].cantidadPagos").value(0))
                .andExpect(jsonPath("$[2].mes").value(3))
                .andExpect(jsonPath("$[2].cantidadPagos").value(1));
    }

    @Test
    @DisplayName("Un pago anulado no suma, y cada mes coincide con /ganancias-mensuales de ese mes")
    void gananciasPorMes_conPagoAnulado_deberiaCoincidirConGananciasMensuales() throws Exception {
        Plan plan = planRepository.findAll().get(0);
        registrarPago(plan, LocalDate.of(2001, 5, 3), false);
        registrarPago(plan, LocalDate.of(2001, 5, 20), true);
        String token = loguearComoAdmin();

        MvcResult serie = mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .param("desde", "2001-05").param("hasta", "2001-05")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalGanancias").value(plan.getPrecio()))
                .andExpect(jsonPath("$[0].cantidadPagos").value(1))
                .andReturn();

        MvcResult mensual = mockMvc.perform(get("/api/v1/dashboard/ganancias-mensuales")
                        .param("anio", "2001").param("mes", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode mesDeLaSerie = objectMapper.readTree(serie.getResponse().getContentAsString()).get(0);
        JsonNode mesSuelto = objectMapper.readTree(mensual.getResponse().getContentAsString());
        assertEquals(mesSuelto, mesDeLaSerie);
    }

    @Test
    @DisplayName("Sin parametros trae los ultimos 12 meses")
    void gananciasPorMes_sinParametros_deberiaTraerDoceMeses() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)));
    }

    @Test
    @DisplayName("Un rango invertido devuelve 400")
    void gananciasPorMes_conRangoInvertido_deberiaDevolver400() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .param("desde", "2026-09").param("hasta", "2026-01")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("'desde' no puede ser posterior a 'hasta'"));
    }

    @Test
    @DisplayName("Un mes mal formado devuelve 400 diciendo que parametro y que formato")
    void gananciasPorMes_conFormatoInvalido_deberiaDevolver400() throws Exception {
        String token = loguearComoAdmin();
        for (String invalido : new String[]{"2026-9", "09-2026", "2026-13", "abc"}) {
            mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                            .param("desde", invalido).param("hasta", "2026-09")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("El parámetro 'desde' debe tener el formato AAAA-MM"));
        }
    }

    @Test
    @DisplayName("Un rango de mas de 24 meses devuelve 400; 24 exactos se aceptan")
    void gananciasPorMes_conRangoDemasiadoLargo_deberiaDevolver400() throws Exception {
        String token = loguearComoAdmin();
        mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .param("desde", "2001-01").param("hasta", "2003-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El rango no puede superar los 24 meses"));

        mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .param("desde", "2001-01").param("hasta", "2002-12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)));
    }

    @Test
    @DisplayName("Un año que no es un número devuelve 400 nombrando el parámetro, no 500")
    void gananciasMensuales_conAnioNoNumerico_deberiaDevolver400() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/ganancias-mensuales").param("anio", "abc")
                        .header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'anio' tiene un valor inválido"));
    }

    @Test
    @DisplayName("GERENCIA no accede a la serie de ganancias")
    void gananciasPorMes_conTokenGerencia_deberiaDevolver403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/ganancias-por-mes")
                        .header("Authorization", "Bearer " + loguearComoGerencia()))
                .andExpect(status().isForbidden());
    }

    private void registrarPago(Plan plan, LocalDate fechaPago, boolean anulado) {
        String documento = "SERIE" + System.nanoTime();
        Cliente cliente = clienteRepository.save(new Cliente(null, "Serie", "Mes", "555-" + documento,
                documento, null, null, EstadoCliente.INACTIVO, null));
        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setPlan(plan);
        pago.setMontoAbonado(plan.getPrecio());
        pago.setFechaPago(fechaPago);
        pago.setFechaVencimiento(fechaPago.plusDays(plan.getDuracion()));
        pago.setAnulado(anulado);
        pagoRepository.save(pago);
    }

    private String loguearComoAdmin() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789"))))
                .andExpect(status().isOk())
                .andReturn();
        return extraerToken(resultado);
    }

    private String loguearComoGerencia() throws Exception {
        // No hay un GERENCIA sembrado: lo crea un ADMIN, como en la vida real.
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"gerencia.dashboard\",\"contrasena\":\"claveGerencia123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated());

        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("gerencia.dashboard", "claveGerencia123"))))
                .andExpect(status().isOk())
                .andReturn();
        return extraerToken(resultado);
    }

    private String extraerToken(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asText();
    }
}

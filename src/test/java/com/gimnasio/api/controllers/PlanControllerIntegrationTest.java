package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.ClienteLoginRequest;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de /api/v1/planes. Leer el catálogo alcanza con estar logueado
 * (cualquier rol, también el socio); crearlo, editarlo y borrarlo es solo de ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanControllerIntegrationTest {

    private static final String PLAN_VALIDO = "{\"nombre\":\"Plan Test\",\"precio\":15000,\"duracion\":30}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("Sin token no se puede ni leer el catálogo")
    void listar_sinToken_deberiaDevolver401() throws Exception {
        mockMvc.perform(get("/api/v1/planes")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un socio logueado puede leer el catálogo de planes")
    void listar_conTokenDeCliente_deberiaDevolver200() throws Exception {
        mockMvc.perform(get("/api/v1/planes").header("Authorization", "Bearer " + loguearComoCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("ADMIN crea un plan y el id que venga en el body se ignora")
    void crear_conTokenAdmin_deberiaDevolver201() throws Exception {
        Integer idExistente = planRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/planes")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + idExistente + ",\"nombre\":\"Plan Test\",\"precio\":15000,\"duracion\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Plan Test"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(idExistente)));

        // El plan que ya existía no fue pisado.
        assertTrue(planRepository.findById(idExistente).map(p -> !p.getNombre().equals("Plan Test")).orElse(false));
    }

    @Test
    @DisplayName("Ni GERENCIA ni un socio pueden crear planes")
    void crear_sinRolAdmin_deberiaDevolver403() throws Exception {
        for (String token : new String[]{loguearComoGerencia(), loguearComoCliente()}) {
            mockMvc.perform(post("/api/v1/planes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PLAN_VALIDO))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Ni GERENCIA ni un socio pueden editar ni borrar planes")
    void editarYBorrar_sinRolAdmin_deberiaDevolver403() throws Exception {
        Integer id = planRepository.findAll().get(0).getId();
        for (String token : new String[]{loguearComoGerencia(), loguearComoCliente()}) {
            mockMvc.perform(put("/api/v1/planes/" + id)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PLAN_VALIDO))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/planes/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Precio cero o negativo, y nombre vacío, se rechazan con 400")
    void crear_conDatosInvalidos_deberiaDevolver400() throws Exception {
        String tokenAdmin = loguearComoAdmin();
        for (String cuerpo : new String[]{
                "{\"nombre\":\"X\",\"precio\":0,\"duracion\":30}",
                "{\"nombre\":\"X\",\"precio\":-1,\"duracion\":30}",
                "{\"nombre\":\"  \",\"precio\":100,\"duracion\":30}",
                "{\"nombre\":\"X\",\"precio\":100.999,\"duracion\":30}",
                "{\"nombre\":\"X\",\"precio\":100,\"duracion\":0}"}) {
            mockMvc.perform(post("/api/v1/planes")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("ADMIN edita un plan existente")
    void actualizar_conTokenAdmin_deberiaDevolver200() throws Exception {
        Plan plan = planRepository.save(new Plan(null, "Viejo", new BigDecimal("100.00"), 30));

        mockMvc.perform(put("/api/v1/planes/" + plan.getId())
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Plan Test"))
                .andExpect(jsonPath("$.duracion").value(30));
    }

    @Test
    @DisplayName("Editar o borrar un plan que no existe da 404")
    void editarYBorrar_planInexistente_deberiaDevolver404() throws Exception {
        String tokenAdmin = loguearComoAdmin();
        mockMvc.perform(put("/api/v1/planes/999999")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN_VALIDO))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/planes/999999").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADMIN borra un plan sin pagos")
    void eliminar_planSinPagos_deberiaDevolver204() throws Exception {
        Plan plan = planRepository.save(new Plan(null, "Descartable", new BigDecimal("100.00"), 30));

        mockMvc.perform(delete("/api/v1/planes/" + plan.getId()).header("Authorization", "Bearer " + loguearComoAdmin()))
                .andExpect(status().isNoContent());

        assertTrue(planRepository.findById(plan.getId()).isEmpty());
    }

    @Test
    @DisplayName("Borrar un plan con pagos se rechaza con 400 y no con un 500 de la FK")
    void eliminar_planConPagos_deberiaDevolver400() throws Exception {
        String tokenAdmin = loguearComoAdmin(); // antes del DELETE: tras la violación de FK la transacción queda abortada
        Plan plan = planRepository.save(new Plan(null, "Con pagos", new BigDecimal("100.00"), 30));
        Cliente cliente = clienteRepository.save(
                new Cliente(null, "Pl", "Pagos", "555-PL1", "555-PL1", null, null, EstadoCliente.ACTIVO, null));
        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setPlan(plan);
        pago.setMontoAbonado(plan.getPrecio());
        pago.setFechaPago(LocalDate.now());
        pago.setFechaVencimiento(LocalDate.now().plusDays(30));
        pagoRepository.saveAndFlush(pago);
        // Sin esto el Pago sigue en la sesión compartida con la request y Hibernate falla antes
        // de llegar a la FK; en producción cada request arranca con la sesión vacía.
        entityManager.clear();

        mockMvc.perform(delete("/api/v1/planes/" + plan.getId()).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("pagos registrados")));
    }

    private String loguearComoAdmin() throws Exception {
        return tokenDe(post("/api/v1/usuarios/login"), objectMapper.writeValueAsString(new LoginRequest("admin", "admin123456789")));
    }

    private String loguearComoGerencia() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + loguearComoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"gerencia.planes\",\"contrasena\":\"claveGerencia123\",\"rol\":\"GERENCIA\"}"))
                .andExpect(status().isCreated());
        return tokenDe(post("/api/v1/usuarios/login"),
                objectMapper.writeValueAsString(new LoginRequest("gerencia.planes", "claveGerencia123")));
    }

    private String loguearComoCliente() throws Exception {
        clienteRepository.save(new Cliente(null, "Socio", "Planes", "555-PL2", "555-PL2", null, null, EstadoCliente.ACTIVO, "555-PL2"));
        mockMvc.perform(post("/api/v1/clientes/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClienteRegistroRequest("555-PL2", "socio.planes@test.com", "claveSocio123"))))
                .andExpect(status().isOk());
        return tokenDe(post("/api/v1/clientes/login"),
                objectMapper.writeValueAsString(new ClienteLoginRequest("socio.planes@test.com", "claveSocio123")));
    }

    private String tokenDe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login, String cuerpo) throws Exception {
        MvcResult resultado = mockMvc.perform(login.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asText();
    }
}

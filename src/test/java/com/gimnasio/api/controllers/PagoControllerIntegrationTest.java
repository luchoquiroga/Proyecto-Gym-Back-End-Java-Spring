package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.ClienteLoginRequest;
import com.gimnasio.api.dto.ClienteRegistroRequest;
import com.gimnasio.api.dto.LoginRequest;
import com.gimnasio.api.dto.PagoRequest;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del scoping por dueño en /api/v1/pagos: un Cliente solo
 * puede ver sus propios pagos, nunca los de otro, y no puede listar el total.
 * Staff (ADMIN/GERENCIA) sigue viendo todo sin restricción.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PagoControllerIntegrationTest {

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

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Un token de Cliente puede ver sus propios pagos")
    void obtenerPagosPorCliente_conTokenPropio_deberiaDevolver200() throws Exception {
        Cliente cliente = crearClienteConPago("Laura", "Diaz", "555-P1", "laura.pago@test.com", "claveLaura123");
        String token = loguearComoCliente("laura.pago@test.com", "claveLaura123");

        mockMvc.perform(get("/api/v1/pagos/cliente/" + cliente.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un token de Cliente no puede ver los pagos de otro cliente")
    void obtenerPagosPorCliente_conTokenDeOtroCliente_deberiaDevolver403() throws Exception {
        Cliente otroCliente = crearClienteConPago("Marta", "Ruiz", "555-P2", null, null);
        crearClienteConPago("Pedro", "Lima", "555-P3", "pedro.pago@test.com", "clavePedro123");
        String token = loguearComoCliente("pedro.pago@test.com", "clavePedro123");

        mockMvc.perform(get("/api/v1/pagos/cliente/" + otroCliente.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token de Cliente no puede listar todos los pagos (listado completo es solo staff)")
    void listarPagos_conTokenDeCliente_deberiaDevolver403() throws Exception {
        crearClienteConPago("Sofia", "Cruz", "555-P4", "sofia.pago@test.com", "claveSofia123");
        String token = loguearComoCliente("sofia.pago@test.com", "claveSofia123");

        mockMvc.perform(get("/api/v1/pagos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token ADMIN puede listar todos los pagos sin restricción")
    void listarPagos_conTokenAdmin_deberiaDevolver200() throws Exception {
        crearClienteConPago("Gaston", "Vera", "555-P5", null, null);
        String tokenAdmin = loguearComoAdmin();

        mockMvc.perform(get("/api/v1/pagos").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Registrar un pago guarda qué usuario de staff lo cobró, tomado del token")
    void registrarPago_deberiaGuardarElAutorDelCobro() throws Exception {
        Cliente cliente = crearClienteConPago("Nadia", "Sosa", "555-P6", null, null);
        Plan plan = planRepository.findAll().getFirst();
        String tokenAdmin = loguearComoAdmin();

        MvcResult resultado = mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PagoRequest(cliente.getId(), plan.getId(), plan.getPrecio(), LocalDate.now()))))
                .andExpect(status().isCreated())
                // El autor es dato de auditoría interno: no viaja en la respuesta, que el
                // propio Cliente dueño del pago puede leer por GET /pagos/{id}.
                .andExpect(jsonPath("$.registradoPor").doesNotExist())
                .andReturn();

        Integer pagoId = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asInt();
        Usuario admin = usuarioRepository.findByNombre("admin").orElseThrow();

        Pago guardado = pagoRepository.findById(pagoId).orElseThrow();
        assertEquals(admin.getId(), guardado.getRegistradoPor().getId());
    }

    @Test
    @DisplayName("Un pago menor al precio del plan se rechaza con 400 y no se registra")
    void registrarPago_conMontoInsuficiente_deberiaDevolver400() throws Exception {
        Cliente cliente = crearClienteConPago("Ivan", "Rojas", "555-P7", null, null);
        Plan plan = planRepository.findAll().getFirst();
        String tokenAdmin = loguearComoAdmin();
        long pagosAntes = pagoRepository.count();

        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PagoRequest(cliente.getId(), plan.getId(), 1.0, LocalDate.now()))))
                .andExpect(status().isBadRequest());

        assertEquals(pagosAntes, pagoRepository.count());
    }

    @Test
    @DisplayName("Un Cliente no puede registrar pagos: cobrar es operación de staff")
    void registrarPago_conTokenDeCliente_deberiaDevolver403() throws Exception {
        Cliente cliente = crearClienteConPago("Elsa", "Mora", "555-P8", "elsa.pago@test.com", "claveElsa123");
        Plan plan = planRepository.findAll().getFirst();
        String token = loguearComoCliente("elsa.pago@test.com", "claveElsa123");

        mockMvc.perform(post("/api/v1/pagos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PagoRequest(cliente.getId(), plan.getId(), plan.getPrecio(), LocalDate.now()))))
                .andExpect(status().isForbidden());
    }

    private Cliente crearClienteConPago(String nombre, String apellido, String telefono,
                                         String email, String contrasena) throws Exception {
        // El teléfono ya es único por test y cabe en el VARCHAR(10) de codigo_activacion,
        // así que sirve como código de activación de prueba sin riesgo de colisión.
        Cliente cliente = clienteRepository.save(
                new Cliente(null, nombre, apellido, telefono, null, null, EstadoCliente.ACTIVO, telefono));

        if (email != null) {
            mockMvc.perform(post("/api/v1/clientes/registro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ClienteRegistroRequest(telefono, email, contrasena))))
                    .andExpect(status().isOk());
        }

        Plan plan = planRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay planes sembrados por DataInitializer"));

        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setPlan(plan);
        pago.setMontoAbonado(plan.getPrecio());
        pago.setFechaPago(LocalDate.now());
        pago.setFechaVencimiento(LocalDate.now().plusDays(plan.getDuracion()));
        pagoRepository.save(pago);

        return cliente;
    }

    private String loguearComoCliente(String email, String contrasena) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/clientes/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClienteLoginRequest(email, contrasena))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String loguearComoAdmin() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asText();
    }
}

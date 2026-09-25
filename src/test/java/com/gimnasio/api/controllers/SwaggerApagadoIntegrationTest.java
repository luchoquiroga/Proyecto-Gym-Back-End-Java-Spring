package com.gimnasio.api.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger con la configuración de producción (SWAGGER_ENABLED sin definir = apagado). Las
 * propiedades van explícitas porque la suite lee src/test/resources/application.properties,
 * no la de main, donde está el default.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
class SwaggerApagadoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Con Swagger apagado, el contrato no se publica: 404 y no 500")
    void apiDocs_conSwaggerApagado_deberiaDevolver404() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}

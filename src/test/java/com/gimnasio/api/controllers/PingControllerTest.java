package com.gimnasio.api.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingControllerTest {

    private final PingController pingController = new PingController();

    @Test
    @DisplayName("GET /ping debería retornar 'OK'")
    void deberiaRetornarOk() {
        String resultado = pingController.ping();
        assertEquals("OK", resultado);
    }
}

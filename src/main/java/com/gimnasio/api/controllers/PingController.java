package com.gimnasio.api.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint liviano para verificar la disponibilidad del servidor
 * y mantener activo el servicio (healthcheck / keep-alive ping).
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "OK";
    }
}

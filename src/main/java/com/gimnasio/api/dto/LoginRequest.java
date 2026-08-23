package com.gimnasio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la solicitud de autenticación / inicio de sesión.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    private String nombre;
    private String contrasena;
}

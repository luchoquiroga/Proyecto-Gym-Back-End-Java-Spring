package com.gimnasio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para que un cliente ya dado de alta por el staff complete su registro
 * en el portal web, sumando email y contraseña a su perfil existente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRegistroRequest {

    private String nombre;
    private String apellido;
    private String telefono;
    private String email;
    private String contrasena;
}

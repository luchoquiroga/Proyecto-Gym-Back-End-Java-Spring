package com.gimnasio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la solicitud de cambio o recuperación de contraseña.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambioContrasenaRequest {

    private String nombre;
    private String nuevaContrasena;
}

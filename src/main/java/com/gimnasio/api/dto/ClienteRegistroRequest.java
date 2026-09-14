package com.gimnasio.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para que un cliente ya dado de alta por el staff complete su registro en el
 * portal web, sumando email y contraseña a su perfil existente. Se identifica con el
 * código de activación de un solo uso que el staff le entregó en persona (no con
 * datos como nombre/apellido/teléfono, adivinables por un tercero).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRegistroRequest {

    @NotBlank(message = "El código de activación es obligatorio")
    private String codigoActivacion;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;
}

package com.gimnasio.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    // 72 porque BCrypt solo usa los primeros 72 bytes: más largo, el encoder la rechaza con
    // un error en inglés, y antes ni eso (dos claves con el mismo comienzo eran iguales).
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String contrasena;
}

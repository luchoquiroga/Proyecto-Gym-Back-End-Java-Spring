package com.gimnasio.api.dto;

import com.gimnasio.api.models.enums.RolUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el alta de un usuario de staff (ADMIN/GERENCIA). Sin campo id: el
 * alta nunca debe poder pisar otra fila vía un id enviado en el body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequest {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 100, message = "El nombre de usuario no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "La contraseña es obligatoria")
    // 72 porque BCrypt solo usa los primeros 72 bytes: más largo, el encoder la rechaza con
    // un error en inglés, y antes ni eso (dos claves con el mismo comienzo eran iguales).
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String contrasena;

    @NotNull(message = "El rol es obligatorio")
    private RolUsuario rol;
}

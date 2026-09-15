package com.gimnasio.api.dto;

import com.gimnasio.api.models.enums.RolUsuario;
import jakarta.validation.constraints.NotBlank;
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
    private String nombre;

    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;

    @NotNull(message = "El rol es obligatorio")
    private RolUsuario rol;
}

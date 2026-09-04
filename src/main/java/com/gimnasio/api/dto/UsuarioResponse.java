package com.gimnasio.api.dto;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos públicos de un usuario, seguros para exponer en respuestas de autenticación.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private Integer id;
    private String nombre;
    private RolUsuario rol;

    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getRol());
    }
}

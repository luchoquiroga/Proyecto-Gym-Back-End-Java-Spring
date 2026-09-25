package com.gimnasio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cambio de la contraseña <b>propia</b> ({@code PUT /api/v1/usuarios/cambiar-contrasena}).
 *
 * <p>No tiene campo {@code nombre} a propósito, y eso es el punto del DTO: antes lo tenía,
 * así que quien llamaba elegía desde el body a qué cuenta le cambiaba la clave. Ahora la
 * cuenta sale del {@code AuthPrincipal} (el token), nunca del body — la misma regla que
 * se aplica a {@code pagos.registrado_por}.
 *
 * <p>Pedir la contraseña actual es lo que separa este endpoint de un reset administrativo:
 * sin ella, una sesión abierta y olvidada alcanzaría para quedarse con la cuenta. El reset
 * sin contraseña actual existe aparte, es solo de ADMIN y no se puede usar contra uno mismo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambioContrasenaRequest {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String contrasenaActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    // 72 porque BCrypt solo usa los primeros 72 bytes: más largo, el encoder la rechaza con
    // un error en inglés, y antes ni eso (dos claves con el mismo comienzo eran iguales).
    @Size(min = 8, max = 72, message = "La nueva contraseña debe tener entre 8 y 72 caracteres")
    private String nuevaContrasena;
}

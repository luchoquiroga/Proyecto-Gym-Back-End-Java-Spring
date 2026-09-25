package com.gimnasio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reset administrativo de la contraseña de otra cuenta de staff
 * ({@code PUT /api/v1/usuarios/{id}/contrasena}, solo ADMIN).
 *
 * <p>Existe para el caso real de "un empleado se olvidó la clave" — no hay email de staff
 * en el modelo, así que no hay recuperación automática posible. Por eso no pide la
 * contraseña actual: el ADMIN no la sabe.
 *
 * <p>A quién se le cambia la clave viaja en la URL, no acá, y el service rechaza que el id
 * sea el del propio ADMIN: para la cuenta propia está
 * {@link CambioContrasenaRequest}, que sí exige la contraseña actual.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetContrasenaRequest {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    // 72 porque BCrypt solo usa los primeros 72 bytes: más largo, el encoder la rechaza con
    // un error en inglés, y antes ni eso (dos claves con el mismo comienzo eran iguales).
    @Size(min = 8, max = 72, message = "La nueva contraseña debe tener entre 8 y 72 caracteres")
    private String nuevaContrasena;
}

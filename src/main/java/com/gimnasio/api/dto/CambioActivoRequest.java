package com.gimnasio.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alta o baja de una cuenta de staff ({@code PATCH /api/v1/usuarios/{id}/activo}, solo ADMIN).
 *
 * <p>Su razón de ser es poder <b>revertir</b> una baja. Como {@code usuarios.nombre} es
 * UNIQUE desde V4, una cuenta dada de baja sigue ocupando su nombre de login: quien se
 * equivocó de fila no puede arreglarlo creando otra igual, tiene que reactivar esta.
 *
 * <p>{@code activo} es obligatorio y no tiene default: un body vacío no debe decidir en
 * silencio si una cuenta queda habilitada o no.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambioActivoRequest {

    @NotNull(message = "Hay que indicar si la cuenta queda activa o no")
    private Boolean activo;
}

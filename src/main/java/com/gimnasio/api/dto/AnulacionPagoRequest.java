package com.gimnasio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anulación de un pago cargado por error
 * ({@code POST /api/v1/pagos/{id}/anulacion}, solo ADMIN).
 *
 * <p>Es POST y no DELETE a propósito: no se borra nada, se agrega un hecho. El pago queda
 * en la tabla marcado como anulado y se sigue viendo en los listados; lo que cambia es que
 * deja de contar para las ganancias y para la fecha de vencimiento del socio.
 *
 * <p>El motivo es obligatorio porque es lo único que hace que conservar el pago anulado
 * valga la pena: una fila anulada sin explicación no es auditoría, es ruido.
 *
 * <p>Quién anula no viaja acá: sale del {@code AuthPrincipal}, igual que el autor del cobro
 * (§5.5 del replanteo). Tampoco hay forma de editar un pago: corregir un importe es anular
 * este y registrar el correcto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnulacionPagoRequest {

    @NotBlank(message = "El motivo de la anulación es obligatorio")
    @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
    private String motivo;
}

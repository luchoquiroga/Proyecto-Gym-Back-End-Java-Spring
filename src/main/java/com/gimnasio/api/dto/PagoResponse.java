package com.gimnasio.api.dto;

import com.gimnasio.api.models.Pago;

import java.time.LocalDate;

/**
 * Datos de un pago seguros para exponer en respuestas de lectura.
 *
 * A propósito NO incluye {@code registradoPor} (el usuario de staff que cobró): tanto
 * GET /pagos/{id} como GET /pagos/cliente/{clienteId} los puede leer el propio CLIENTE
 * dueño del pago, y no hay motivo para contarle a un socio qué empleado lo atendió. Es
 * un dato de auditoría interno (ver {@link Pago#getRegistradoPor()}), no de negocio: se
 * consulta directo contra la base cuando haga falta, no por este DTO.
 */
public record PagoResponse(
        Integer id,
        Double montoAbonado,
        LocalDate fechaPago,
        LocalDate fechaVencimiento,
        ClienteResumen cliente,
        PlanResumen plan,
        /**
         * Si el pago fue anulado por haberse cargado por error. Se expone —y el pago se
         * sigue listando— a propósito: esconderlo sería volver al borrado por la ventana,
         * que es justamente lo que la anulación evita. No cuenta para las ganancias ni
         * para la fecha de vencimiento del socio.
         */
        boolean anulado
) {

    public record ClienteResumen(Integer id, String nombre, String apellido) {
    }

    public record PlanResumen(Integer id, String nombre) {
    }

    public static PagoResponse desde(Pago pago) {
        return new PagoResponse(
                pago.getId(),
                pago.getMontoAbonado(),
                pago.getFechaPago(),
                pago.getFechaVencimiento(),
                new ClienteResumen(pago.getCliente().getId(), pago.getCliente().getNombre(), pago.getCliente().getApellido()),
                new PlanResumen(pago.getPlan().getId(), pago.getPlan().getNombre()),
                pago.isAnulado()
        );
    }
}

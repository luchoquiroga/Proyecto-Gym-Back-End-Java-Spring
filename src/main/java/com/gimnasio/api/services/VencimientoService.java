package com.gimnasio.api.services;

/**
 * Define la lógica de negocio para actualizar el estado de los clientes
 * en función del vencimiento de su último pago.
 */
public interface VencimientoService {

    /**
     * Revisa el último pago de cada cliente y actualiza su estado:
     * - MOROSO si pasó 1 día o más desde el vencimiento.
     * - INACTIVO si pasaron 5 días o más desde el vencimiento.
     * Nunca revierte un estado a uno menos severo (no reactiva clientes).
     */
    void actualizarEstadosPorVencimiento();

    /**
     * Recalcula el estado de UN cliente a partir de su último pago vigente, aplicando la
     * misma regla y los mismos umbrales que la corrida diaria. Existe para la anulación de
     * pagos: anular el último pago de un socio puede dejarlo con un vencimiento ya pasado,
     * y sin esto seguiría figurando ACTIVO hasta la próxima corrida del scheduler.
     *
     * <p>Igual que la corrida diaria, solo escala el estado y nunca lo revierte. Acá eso
     * alcanza porque anular un pago únicamente puede empeorar la situación del socio.
     *
     * @param clienteId el socio a recalcular.
     */
    void recalcularEstadoDe(Integer clienteId);
}

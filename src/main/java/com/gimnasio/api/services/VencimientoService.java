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
}

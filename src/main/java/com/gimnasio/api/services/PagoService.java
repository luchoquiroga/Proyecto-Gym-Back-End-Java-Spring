package com.gimnasio.api.services;

import com.gimnasio.api.models.Pago;

import java.time.LocalDate;
import java.util.List;

/**
 * Interfaz que define las operaciones de negocio para la gestión y registro de Pagos.
 */
public interface PagoService {

    /**
     * Obtiene el listado histórico de todos los pagos registrados.
     */
    List<Pago> obtenerTodos();

    /**
     * Obtiene un pago por su ID único.
     * @param id Identificador del pago.
     * @return El pago encontrado.
     * @throws RuntimeException si el pago no existe.
     */
    Pago obtenerPorId(Integer id);

    /**
     * Obtiene el historial de pagos asociados a un cliente específico por su ID.
     * @param clienteId ID del cliente.
     * @return Lista de pagos del cliente.
     */
    List<Pago> obtenerPagosPorCliente(Integer clienteId);

    /**
     * Busca los pagos filtrando por el nombre del cliente (para la interfaz de usuario).
     * @param nombreCliente Nombre o fragmento de nombre del cliente.
     * @return Lista de pagos que coincidan.
     */
    List<Pago> buscarPagosPorNombreCliente(String nombreCliente);

    /**
     * Registra un nuevo pago en el sistema y calcula automáticamente la fecha de
     * vencimiento según la duración del plan.
     *
     * Reglas de negocio:
     * - El monto no puede ser menor al precio del plan: no existe el pago parcial,
     *   un pago insuficiente se rechaza y no se registra.
     * - El cliente pasa a ACTIVO solo si el vencimiento calculado es futuro, para que
     *   un pago retroactivo ya vencido no reactive a alguien que no está al día.
     *
     * @param clienteId ID del cliente que abona.
     * @param planId ID del plan contratado.
     * @param montoAbonado Monto que paga (si es nulo, toma el precio oficial del plan).
     * @param fechaPago Fecha en que se realiza el pago (si es nula, toma la fecha de hoy).
     * @param registradoPorId ID del usuario de staff que cobra, tomado del token del
     *                        que llama y nunca del body: es el rastro de auditoría.
     * @return El pago registrado y persistido.
     * @throws IllegalArgumentException si el monto es menor al precio del plan.
     */
    Pago registrarPago(Integer clienteId, Integer planId, Double montoAbonado, LocalDate fechaPago,
                       Integer registradoPorId);
}

package com.gimnasio.api.services;

import com.gimnasio.api.models.Pago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Interfaz que define las operaciones de negocio para la gestión y registro de Pagos.
 */
public interface PagoService {

    /**
     * Obtiene el listado histórico de pagos registrados, paginado: es el listado completo
     * sin acotar por cliente, así que sin paginar podía devolver toda la tabla en una sola
     * respuesta.
     *
     * <p>Los dos límites son opcionales e independientes y se aplican sobre la fecha de
     * cobro: sin ninguno devuelve todo, como antes. Existen para poder desglosar las
     * ganancias de un mes —ver qué pagos componen el total que informa el dashboard— sin
     * traerse el histórico entero para filtrarlo en el navegador.
     *
     * @param desde primera fecha de cobro incluida, o null para no acotar por abajo.
     * @param hasta última fecha de cobro incluida, o null para no acotar por arriba.
     * @throws IllegalArgumentException si `desde` es posterior a `hasta`.
     */
    Page<Pago> obtenerTodos(LocalDate desde, LocalDate hasta, Pageable pageable);

    /**
     * Anula un pago cargado por error: la fila se conserva —con quién lo anuló, cuándo y
     * por qué— pero deja de contar para las ganancias y para la fecha de vencimiento del
     * socio, y el estado del socio se recalcula en el momento.
     *
     * <p>No existe borrar un pago ni editarle el monto: corregir un importe es anular este
     * y registrar el correcto. Ver la migración V6 para el detalle.
     *
     * @param id identificador del pago a anular.
     * @param motivo por qué se anula; obligatorio, es lo que hace que la fila conservada
     *               sirva como auditoría.
     * @param anuladoPorId quién anula, tomado del token y nunca del body.
     * @throws IllegalArgumentException si el pago ya estaba anulado.
     */
    Pago anular(Integer id, String motivo, Integer anuladoPorId);

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

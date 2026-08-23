package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;

import java.util.List;

/**
 * Interfaz que define el contrato de operaciones de negocio para la gestión de Clientes.
 */
public interface ClienteService {

    /**
     * Obtiene la lista completa de todos los clientes registrados.
     */
    List<Cliente> obtenerTodos();

    /**
     * Busca un cliente unívocamente por su clave primaria (ID).
     * @param id Identificador único del cliente.
     * @return El cliente encontrado.
     * @throws RuntimeException si el cliente no existe.
     */
    Cliente obtenerPorId(Integer id);

    /**
     * Busca un cliente por su nombre.
     * @param nombre Nombre del cliente a buscar.
     * @return El cliente encontrado.
     * @throws RuntimeException si no se encuentra un cliente con ese nombre.
     */
    Cliente buscarPorNombre(String nombre);

    /**
     * Registra un nuevo cliente en el sistema.
     * Su estado inicial será INACTIVO hasta que registre su primer pago.
     * @param cliente Datos del nuevo cliente.
     * @return El cliente registrado y persistido con su ID generado.
     */
    Cliente crear(Cliente cliente);

    /**
     * Actualiza los datos de contacto de un cliente existente.
     * @param id Identificador del cliente a actualizar.
     * @param clienteActualizado Nuevos datos (nombre, apellido, teléfono).
     * @return El cliente actualizado.
     */
    Cliente actualizar(Integer id, Cliente clienteActualizado);

    /**
     * Cambia manualmente el estado de un cliente (ACTIVO, MOROSO, INACTIVO).
     * @param id Identificador del cliente.
     * @param nuevoEstado Nuevo estado a asignar.
     * @return El cliente con el estado actualizado.
     */
    Cliente cambiarEstado(Integer id, EstadoCliente nuevoEstado);

    /**
     * Da de baja lógica (Soft Delete) a un cliente, pasando su estado a INACTIVO.
     * Mantiene intacta la integridad referencial y el historial de pagos.
     * @param id Identificador del cliente a dar de baja.
     */
    void darDeBaja(Integer id);
}

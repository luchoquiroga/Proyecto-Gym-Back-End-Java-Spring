package com.gimnasio.api.services;

import com.gimnasio.api.dto.ClienteResponse;
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

    /**
     * Completa el registro web de un cliente ya dado de alta por el staff (sin credenciales),
     * sumándole email y contraseña a su perfil existente, identificado por el código de
     * activación de un solo uso que el staff le entregó en persona. No crea un cliente nuevo.
     * El código se anula al usarse, así que nunca sirve dos veces.
     * @throws IllegalArgumentException si el código es inválido/ya usado, el cliente ya
     *         tiene una cuenta, o el email está en uso.
     */
    Cliente registrarCredenciales(String codigoActivacion, String email, String contrasena);

    /**
     * Autentica las credenciales de un cliente al iniciar sesión en el portal web.
     */
    boolean autenticar(String email, String contrasena);

    /**
     * Busca un cliente por su email de login.
     * @throws RuntimeException si no existe.
     */
    Cliente buscarPorEmail(String email);

    /**
     * Lista todos los clientes con su fecha de vencimiento vigente (la del último pago
     * registrado de cada uno, o null si nunca pagó), pero sin ningún dato monetario.
     * Existe para que GERENCIA pueda saber quién está al día desde el listado de socios
     * sin necesitar leer la tabla de pagos (a la que ya no tiene acceso). Resuelve las
     * fechas de todos los clientes en una sola consulta para evitar un N+1.
     * @return la lista de clientes como {@link ClienteResponse}, en el mismo orden que
     *         devuelve el repositorio.
     */
    List<ClienteResponse> obtenerTodosConVencimiento();

    /**
     * Busca un cliente por ID y arma su respuesta pública, incluyendo la fecha de
     * vencimiento de su último pago (o null si nunca pagó). Nunca incluye montos.
     * @param id Identificador único del cliente.
     * @return el cliente encontrado como {@link ClienteResponse}.
     * @throws RuntimeException si el cliente no existe.
     */
    ClienteResponse obtenerRespuestaPorId(Integer id);

    /**
     * Busca un cliente por nombre y arma su respuesta pública, incluyendo la fecha de
     * vencimiento de su último pago (o null si nunca pagó). Nunca incluye montos.
     * @param nombre Nombre del cliente a buscar.
     * @return el cliente encontrado como {@link ClienteResponse}.
     * @throws RuntimeException si no se encuentra un cliente con ese nombre.
     */
    ClienteResponse buscarPorNombreConVencimiento(String nombre);
}

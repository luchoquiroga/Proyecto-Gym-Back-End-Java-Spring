package com.gimnasio.api.services;

import com.gimnasio.api.dto.ClienteRequest;
import com.gimnasio.api.dto.ClienteResponse;
import com.gimnasio.api.dto.PaginaResponse;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interfaz que define el contrato de operaciones de negocio para la gestión de Clientes.
 */
public interface ClienteService {




    /**
     * Registra un nuevo cliente en el sistema a partir de los datos que puede
     * enviar el llamador (nombre, apellido, teléfono, email, contraseña opcional).
     * Su estado inicial siempre es INACTIVO hasta que registre su primer pago; el
     * DTO de entrada ni siquiera tiene un campo "estado" ni "id" que se pueda pisar.
     * @param request Datos del nuevo cliente.
     * @return El cliente registrado y persistido con su ID generado.
     */
    Cliente crear(ClienteRequest request);

    /**
     * Actualiza los datos de contacto de un cliente existente.
     * @param id Identificador del cliente a actualizar.
     * @param request Nuevos datos (nombre, apellido, teléfono).
     * @return El cliente actualizado, como {@link ClienteResponse}.
     */
    ClienteResponse actualizar(Integer id, ClienteRequest request);

    /**
     * Cambia manualmente el estado de un cliente (ACTIVO, MOROSO, INACTIVO).
     * @param id Identificador del cliente.
     * @param nuevoEstado Nuevo estado a asignar.
     * @return El cliente con el estado actualizado, como {@link ClienteResponse}.
     */
    ClienteResponse cambiarEstado(Integer id, EstadoCliente nuevoEstado);


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
     * Lista, paginado, todos los clientes con su fecha de vencimiento vigente (la del
     * último pago registrado de cada uno, o null si nunca pagó), pero sin ningún dato
     * monetario. Existe para que GERENCIA pueda saber quién está al día desde el listado
     * de socios sin necesitar leer la tabla de pagos (a la que ya no tiene acceso).
     * Resuelve las fechas de vencimiento en una sola consulta (no una por socio) para
     * evitar un N+1, sin importar el tamaño de la página pedida.
     * @param pageable número/tamaño de página pedidos por el llamador.
     * @return la página de clientes como {@link ClienteResponse}.
     */
    PaginaResponse<ClienteResponse> obtenerTodosConVencimiento(Pageable pageable);

    /**
     * Busca un cliente por ID y arma su respuesta pública, incluyendo la fecha de
     * vencimiento de su último pago (o null si nunca pagó). Nunca incluye montos.
     * @param id Identificador único del cliente.
     * @return el cliente encontrado como {@link ClienteResponse}.
     * @throws RuntimeException si el cliente no existe.
     */
    ClienteResponse obtenerRespuestaPorId(Integer id);

    /**
     * Busca clientes por nombre exacto y arma su respuesta pública, incluyendo la fecha
     * de vencimiento de su último pago (o null si nunca pagó). Nunca incluye montos.
     * No lanza excepción si no hay coincidencias: devuelve una lista vacía.
     * @param nombre Nombre del cliente a buscar.
     * @return la lista de coincidencias (vacía si no hay ninguna) como {@link ClienteResponse}.
     */
    List<ClienteResponse> buscarPorNombreConVencimiento(String nombre);
}

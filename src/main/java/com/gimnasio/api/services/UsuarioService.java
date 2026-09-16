package com.gimnasio.api.services;

import com.gimnasio.api.models.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interfaz que define las operaciones de negocio para la autenticación y gestión de Usuarios.
 */
public interface UsuarioService {

    /**
     * Registra un nuevo usuario para acceder a la aplicación de escritorio.
     * @param usuario Datos del usuario a crear.
     * @return El usuario registrado.
     */
    Usuario registrar(Usuario usuario);

    /**
     * Lista las cuentas de staff, paginadas. Incluye las dadas de baja a propósito: son
     * justamente las que hay que poder ver para reactivarlas, y `UsuarioResponse.activo`
     * las distingue de las vigentes.
     */
    Page<Usuario> obtenerTodos(Pageable pageable);

    /**
     * Autentica las credenciales de un usuario al iniciar sesión en la aplicación.
     * Una cuenta dada de baja no autentica aunque la contraseña sea correcta, y el
     * resultado es el mismo que el de una contraseña errónea: quien intenta entrar no
     * tiene por qué enterarse de si la cuenta existe o en qué estado está.
     * @param nombre Nombre de usuario.
     * @param contrasena Contraseña ingresada.
     * @return true si las credenciales son válidas y la cuenta está activa.
     */
    boolean autenticar(String nombre, String contrasena);

    /**
     * Busca un usuario por su nombre de acceso para obtener sus datos y rol.
     * @param nombre Nombre del usuario.
     * @return El usuario encontrado.
     * @throws RuntimeException si no existe.
     */
    Usuario buscarPorNombre(String nombre);

    /**
     * Cambia la contraseña de la cuenta que hace la llamada, verificando antes la actual.
     * Revoca todas sus sesiones abiertas: si la contraseña se cambia porque alguien más la
     * sabía, dejar viva la sesión de ese alguien no tendría sentido.
     * @param usuarioId Identificador del que llama, tomado del token y nunca del body.
     * @param contrasenaActual Contraseña vigente, como prueba de que la cuenta es suya.
     * @param nuevaContrasena Contraseña nueva.
     * @return El usuario actualizado.
     * @throws IllegalArgumentException si la contraseña actual no coincide, o si la nueva
     *         es igual a la actual.
     */
    Usuario cambiarContrasenaPropia(Integer usuarioId, String contrasenaActual, String nuevaContrasena);

    /**
     * Reset administrativo: un ADMIN le fija una contraseña nueva a otra cuenta que se la
     * olvidó, sin conocer la anterior. Revoca las sesiones de la cuenta afectada.
     * @param id Identificador de la cuenta a resetear.
     * @param nuevaContrasena Contraseña nueva.
     * @param callerId Identificador del ADMIN que pide el reset.
     * @return El usuario actualizado.
     * @throws IllegalArgumentException si id coincide con callerId: para la cuenta propia
     *         existe {@link #cambiarContrasenaPropia}, que sí exige la contraseña actual.
     */
    Usuario resetearContrasena(Integer id, String nuevaContrasena, Integer callerId);

    /**
     * Da de baja lógica a una cuenta de staff: queda inactiva y se le revocan las sesiones
     * abiertas, pero la fila se conserva para no perder a quién apunta {@code registrado_por}
     * en los pagos que cobró (ver migración V5). Es idempotente.
     * @param id Identificador del usuario a dar de baja.
     * @param callerId Identificador de quien pide la baja (para evitar auto-eliminarse).
     * @throws IllegalArgumentException si el id coincide con callerId, o si el usuario es el
     *         último ADMIN activo del sistema.
     */
    void darDeBaja(Integer id, Integer callerId);

    /**
     * Activa o desactiva una cuenta de staff. Reactivar es la única salida cuando se dio de
     * baja la cuenta equivocada: el nombre de login sigue ocupado por esa fila (UNIQUE desde
     * V4), así que no se puede "recrear" la cuenta.
     * @param id Identificador del usuario.
     * @param activo Estado deseado.
     * @param callerId Identificador de quien lo pide (no puede desactivarse a sí mismo).
     * @return El usuario actualizado.
     */
    Usuario cambiarActivo(Integer id, boolean activo, Integer callerId);
}

package com.gimnasio.api.services;

import com.gimnasio.api.models.Usuario;

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
     * Autentica las credenciales de un usuario al iniciar sesión en la aplicación.
     * @param nombre Nombre de usuario.
     * @param contrasena Contraseña ingresada.
     * @return true si las credenciales son válidas, false en caso contrario.
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
     * Actualiza la contraseña de un usuario en caso de olvido o cambio de clave.
     * @param nombre Nombre del usuario.
     * @param nuevaContrasena Nueva contraseña.
     * @return El usuario actualizado.
     */
    Usuario actualizarContrasena(String nombre, String nuevaContrasena);

    /**
     * Elimina una cuenta de usuario del sistema por su ID.
     * @param id Identificador del usuario.
     */
    void eliminar(Integer id);
}

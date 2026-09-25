package com.gimnasio.api.services.impl;

import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.security.RefreshTokenService;
import com.gimnasio.api.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de la lógica de negocio para la gestión y autenticación de Usuarios.
 */
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    // Una baja o un cambio de contraseña tienen que cerrar las sesiones abiertas de esa cuenta,
    // o el cambio no se nota hasta que expire el último refresh token (30 días).
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public Usuario registrar(Usuario usuario) {
        if (usuario.getNombre() == null || usuario.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (usuario.getContrasena() == null || usuario.getContrasena().trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
        // Es el identificador de login: "juan " y "juan" no pueden ser dos cuentas distintas,
        // y nadie tipea el espacio del final al loguearse.
        usuario.setNombre(usuario.getNombre().trim());
        // Una cuenta dada de baja sigue ocupando su nombre (UNIQUE desde V4), así que el
        // duplicado se avisa distinto: el camino no es crear otra igual sino reactivar esa.
        usuarioRepository.findByNombre(usuario.getNombre()).ifPresent(existente -> {
            if (existente.isActivo()) {
                throw new IllegalArgumentException(
                        "Ya existe un usuario registrado con el nombre: " + usuario.getNombre());
            }
            throw new IllegalArgumentException(
                    "El nombre '" + usuario.getNombre() + "' pertenece a una cuenta dada de baja. "
                            + "Reactivala en vez de crear una nueva.");
        });
        // Un alta nunca debe poder pisar una fila existente vía un id enviado en el body.
        usuario.setId(null);
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Usuario> obtenerTodos(Pageable pageable) {
        return usuarioRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean autenticar(String nombre, String contrasena) {
        return usuarioRepository.findByNombre(nombre)
                .map(usuario -> usuario.isActivo() && passwordEncoder.matches(contrasena, usuario.getContrasena()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario buscarPorNombre(String nombre) {
        return usuarioRepository.findByNombre(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con el nombre: " + nombre));
    }

    @Override
    @Transactional
    public Usuario cambiarContrasenaPropia(Integer usuarioId, String contrasenaActual, String nuevaContrasena) {
        Usuario usuario = buscarPorId(usuarioId);

        if (!passwordEncoder.matches(contrasenaActual, usuario.getContrasena())) {
            // 400 y no 401: el token es válido, lo que está mal es un dato del body. Un 401
            // haría que el frontend crea que se venció la sesión y mande a loguearse de nuevo.
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        if (passwordEncoder.matches(nuevaContrasena, usuario.getContrasena())) {
            throw new IllegalArgumentException("La nueva contraseña tiene que ser distinta de la actual.");
        }

        return guardarContrasena(usuario, nuevaContrasena);
    }

    @Override
    @Transactional
    public Usuario resetearContrasena(Integer id, String nuevaContrasena, Integer callerId) {
        if (id.equals(callerId)) {
            // Sin esto, un ADMIN podría cambiarse su propia clave sin saber la anterior, y la
            // verificación de cambiarContrasenaPropia sería decorativa para el rol que importa.
            throw new IllegalArgumentException(
                    "Para cambiar tu propia contraseña usá /cambiar-contrasena, que pide la actual.");
        }

        Usuario usuario = buscarPorId(id);
        return guardarContrasena(usuario, nuevaContrasena);
    }


    @Override
    @Transactional
    public Usuario cambiarActivo(Integer id, boolean activo, Integer callerId) {
        if (!activo && id.equals(callerId)) {
            throw new IllegalArgumentException("No podés dar de baja tu propio usuario.");
        }

        Usuario usuario = buscarPorId(id);

        // Se cuentan los ADMIN activos, no todos: un ADMIN dado de baja no puede loguearse,
        // así que no sirve para administrar el sistema. Contar todos dejaría dar de baja al
        // último que queda en pie mientras hubiera otro inactivo en la tabla.
        if (!activo && usuario.isActivo() && usuario.getRol() == RolUsuario.ADMIN
                && usuarioRepository.countByRolAndActivoTrue(RolUsuario.ADMIN) <= 1) {
            throw new IllegalArgumentException("No se puede dar de baja al último administrador del sistema.");
        }

        usuario.setActivo(activo);
        Usuario guardado = usuarioRepository.save(usuario);

        if (!activo) {
            // La fila queda, pero las sesiones no: sin esto podría seguir renovando su token
            // indefinidamente y la baja no se notaría nunca.
            refreshTokenService.revocarTodosDe(id);
        }

        return guardado;
    }

    private Usuario buscarPorId(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID " + id));
    }

    private Usuario guardarContrasena(Usuario usuario, String nuevaContrasena) {
        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        Usuario guardado = usuarioRepository.save(usuario);
        refreshTokenService.revocarTodosDe(usuario.getId());
        return guardado;
    }
}

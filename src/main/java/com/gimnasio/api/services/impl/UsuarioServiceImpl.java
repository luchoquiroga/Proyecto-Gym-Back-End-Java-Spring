package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.services.UsuarioService;
import lombok.RequiredArgsConstructor;
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

    @Override
    @Transactional
    public Usuario registrar(Usuario usuario) {
        if (usuario.getNombre() == null || usuario.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (usuario.getContrasena() == null || usuario.getContrasena().trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
        if (usuarioRepository.findByNombre(usuario.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con el nombre: " + usuario.getNombre());
        }
        // Un alta nunca debe poder pisar una fila existente vía un id enviado en el body.
        usuario.setId(null);
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean autenticar(String nombre, String contrasena) {
        return usuarioRepository.findByNombre(nombre)
                .map(usuario -> passwordEncoder.matches(contrasena, usuario.getContrasena()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario buscarPorNombre(String nombre) {
        return usuarioRepository.findByNombre(nombre)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con el nombre: " + nombre));
    }

    @Override
    @Transactional
    public Usuario actualizarContrasena(String nombre, String nuevaContrasena) {
        if (nuevaContrasena == null || nuevaContrasena.trim().isEmpty()) {
            throw new IllegalArgumentException("La nueva contraseña no puede estar vacía.");
        }
        Usuario usuario = buscarPorNombre(nombre);
        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public void eliminar(Integer id, Integer callerId) {
        if (id.equals(callerId)) {
            throw new IllegalArgumentException("No podés eliminar tu propio usuario.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se puede eliminar: Usuario no encontrado con ID " + id));

        if (usuario.getRol() == RolUsuario.ADMIN && usuarioRepository.countByRol(RolUsuario.ADMIN) <= 1) {
            throw new IllegalArgumentException("No se puede eliminar el último administrador del sistema.");
        }

        usuarioRepository.deleteById(id);
    }
}

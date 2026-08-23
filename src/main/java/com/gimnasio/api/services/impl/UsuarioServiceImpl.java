package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de la lógica de negocio para la gestión y autenticación de Usuarios.
 */
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

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
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean autenticar(String nombre, String contrasena) {
        return usuarioRepository.findByNombre(nombre)
                .map(usuario -> usuario.getContrasena().equals(contrasena))
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
        usuario.setContrasena(nuevaContrasena);
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        if (!usuarioRepository.existsById(id)) {
            throw new RuntimeException("No se puede eliminar: Usuario no encontrado con ID " + id);
        }
        usuarioRepository.deleteById(id);
    }
}

package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByNombre(String nombre);

    // Cuenta solo las cuentas vigentes: para saber si queda algún ADMIN que pueda administrar
    // el sistema, un ADMIN dado de baja no sirve (no puede loguearse). Contar todas dejaría
    // dar de baja al último ADMIN activo mientras hubiera otro inactivo en la tabla.
    long countByRolAndActivoTrue(RolUsuario rol);
}

package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByNombre(String nombre);

    // Solo las cuentas vigentes: para saber si queda algún ADMIN que pueda administrar el
    // sistema, un ADMIN dado de baja no sirve (no puede loguearse). Contar todas dejaría dar
    // de baja al último ADMIN activo mientras hubiera otro inactivo en la tabla.
    // Bloquea las filas (SELECT ... FOR UPDATE) hasta que termine la transacción: dos bajas
    // cruzadas simultáneas contaban dos ADMIN cada una y el sistema quedaba sin ninguno. El
    // ORDER BY fija el orden en que se toman los locks, para que dos transacciones que piden
    // las mismas filas no se traben esperándose una a la otra.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.rol = :rol AND u.activo = true ORDER BY u.id")
    List<Usuario> findActivosPorRolParaActualizar(@Param("rol") RolUsuario rol);
}

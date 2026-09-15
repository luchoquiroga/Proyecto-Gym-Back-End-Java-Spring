package com.gimnasio.api.repositories;

import com.gimnasio.api.models.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByJti(String jti);

    // Sesiones vivas de un usuario, para poder cerrarlas todas de una (baja de la cuenta o
    // cambio de contraseña). Solo las no revocadas: volver a revocar las que ya lo están
    // sería reescribir filas sin cambiar nada.
    List<RefreshToken> findByUsuarioIdAndRevocadoFalse(Integer usuarioId);
}

package com.gimnasio.api.repositories;

import com.gimnasio.api.models.ClienteRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ClienteRefreshTokenRepository extends JpaRepository<ClienteRefreshToken, Long> {
    Optional<ClienteRefreshToken> findByJti(String jti);

    // Igual que RefreshTokenRepository.borrarVencidosAntesDe.
    @Modifying
    @Query("DELETE FROM ClienteRefreshToken r WHERE r.expiracion < :limite")
    int borrarVencidosAntesDe(@Param("limite") Instant limite);
}

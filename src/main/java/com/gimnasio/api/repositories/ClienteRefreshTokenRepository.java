package com.gimnasio.api.repositories;

import com.gimnasio.api.models.ClienteRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRefreshTokenRepository extends JpaRepository<ClienteRefreshToken, Long> {
    Optional<ClienteRefreshToken> findByJti(String jti);
}

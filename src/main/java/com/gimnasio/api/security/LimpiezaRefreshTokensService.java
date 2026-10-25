package com.gimnasio.api.security;

import com.gimnasio.api.repositories.ClienteRefreshTokenRepository;
import com.gimnasio.api.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Borra los refresh tokens que vencieron hace más de una semana, de los dos portales.
 *
 * Cada login y cada renovación agregan una fila, y revocar solo la marca: sin esto las dos
 * tablas crecen para siempre (AUTHZ-MATRIX.md lo tenía anotado como pendiente).
 *
 * Borrar es seguro: un token cuya fila no existe se rechaza igual ("Refresh token
 * desconocido", en RefreshTokenService y ClienteRefreshTokenService), y uno vencido ya no
 * servía para nada. No se borran los revocados que todavía no vencieron: no molestan y
 * se van solos cuando vencen.
 *
 * La semana de margen es para poder mirar las sesiones de los últimos días si hace falta
 * investigar algo (quién entró, desde cuándo), no por una necesidad del login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimpiezaRefreshTokensService {

    static final Duration MARGEN = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final ClienteRefreshTokenRepository clienteRefreshTokenRepository;
    private final Clock clock;

    /** @return cuántas filas se borraron entre las dos tablas. */
    @Transactional
    public int borrarVencidos() {
        Instant limite = Instant.now(clock).minus(MARGEN);
        int staff = refreshTokenRepository.borrarVencidosAntesDe(limite);
        int socios = clienteRefreshTokenRepository.borrarVencidosAntesDe(limite);
        log.info(">> [Limpieza] Refresh tokens vencidos borrados: {} de staff, {} de socios", staff, socios);
        return staff + socios;
    }
}

package com.gimnasio.api.security;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.ClienteRefreshToken;
import com.gimnasio.api.repositories.ClienteRefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Gestiona el ciclo de vida de los refresh tokens de Cliente (portal web),
 * en espejo de RefreshTokenService pero sin compartir tabla ni tipo con Usuario/staff.
 */
@Service
@RequiredArgsConstructor
public class ClienteRefreshTokenService {

    private final ClienteRefreshTokenRepository clienteRefreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public String crear(Cliente cliente) {
        String jti = jwtService.generarJti();
        String token = jwtService.generarRefreshTokenCliente(cliente, jti);

        ClienteRefreshToken registro = new ClienteRefreshToken();
        registro.setJti(jti);
        registro.setCliente(cliente);
        registro.setExpiracion(Instant.now().plusMillis(jwtService.getRefreshExpiracionMs()));
        registro.setRevocado(false);
        clienteRefreshTokenRepository.save(registro);

        return token;
    }

    @Transactional(readOnly = true)
    public Cliente validar(String token) {
        if (!jwtService.esRefreshToken(token)) {
            throw new JwtException("El token no es un refresh token válido");
        }

        String jti = jwtService.extraerJti(token);
        ClienteRefreshToken registro = clienteRefreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new JwtException("Refresh token desconocido"));

        if (registro.isRevocado() || registro.getExpiracion().isBefore(Instant.now())) {
            throw new JwtException("Refresh token expirado o revocado");
        }

        return registro.getCliente();
    }

    @Transactional
    public String rotar(String tokenActual, Cliente cliente) {
        revocar(tokenActual);
        return crear(cliente);
    }

    @Transactional
    public void revocar(String token) {
        String jti;
        try {
            jti = jwtService.extraerJti(token);
        } catch (JwtException e) {
            return;
        }

        Optional<ClienteRefreshToken> registro = clienteRefreshTokenRepository.findByJti(jti);
        registro.ifPresent(rt -> {
            rt.setRevocado(true);
            clienteRefreshTokenRepository.save(rt);
        });
    }
}

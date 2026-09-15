package com.gimnasio.api.security;

import com.gimnasio.api.models.RefreshToken;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.repositories.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Gestiona el ciclo de vida de los refresh tokens: emisión, validación contra la
 * base de datos (para poder revocarlos antes de su expiración natural), rotación
 * en cada uso y revocación explícita (logout).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public String crear(Usuario usuario) {
        String jti = jwtService.generarJti();
        String token = jwtService.generarRefreshToken(usuario, jti);

        RefreshToken registro = new RefreshToken();
        registro.setJti(jti);
        registro.setUsuario(usuario);
        registro.setExpiracion(Instant.now().plusMillis(jwtService.getRefreshExpiracionMs()));
        registro.setRevocado(false);
        refreshTokenRepository.save(registro);

        return token;
    }

    /**
     * Valida el refresh token (firma, tipo, vigencia JWT y estado en base de datos)
     * y devuelve el usuario asociado si es válido.
     * @throws JwtException si el token es inválido, expiró o fue revocado.
     */
    @Transactional(readOnly = true)
    public Usuario validar(String token) {
        if (!jwtService.esRefreshToken(token)) {
            throw new JwtException("El token no es un refresh token válido");
        }

        String jti = jwtService.extraerJti(token);
        RefreshToken registro = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new JwtException("Refresh token desconocido"));

        if (registro.isRevocado() || registro.getExpiracion().isBefore(Instant.now())) {
            throw new JwtException("Refresh token expirado o revocado");
        }

        // Una cuenta dada de baja no puede estirar su sesión, aunque su refresh token siga
        // vigente y sin revocar. Es el cierre real de la baja: el access token que ya tenía
        // en la mano sigue sirviendo hasta que expira (el filtro no consulta la base), pero
        // a partir de ahí no hay forma de renovarlo.
        if (!registro.getUsuario().isActivo()) {
            throw new JwtException("La cuenta está dada de baja");
        }

        return registro.getUsuario();
    }

    /**
     * Revoca el refresh token actual y emite uno nuevo para el mismo usuario.
     */
    @Transactional
    public String rotar(String tokenActual, Usuario usuario) {
        revocar(tokenActual);
        return crear(usuario);
    }

    /**
     * Cierra todas las sesiones abiertas de un usuario. Se usa cuando la cuenta deja de ser
     * usable tal como está: una baja, o un cambio/reset de contraseña (si la contraseña se
     * cambió porque alguien más la sabía, dejar viva la sesión de ese alguien no tendría
     * sentido).
     */
    @Transactional
    public void revocarTodosDe(Integer usuarioId) {
        var vigentes = refreshTokenRepository.findByUsuarioIdAndRevocadoFalse(usuarioId);
        vigentes.forEach(rt -> rt.setRevocado(true));
        refreshTokenRepository.saveAll(vigentes);
    }

    @Transactional
    public void revocar(String token) {
        String jti;
        try {
            jti = jwtService.extraerJti(token);
        } catch (JwtException e) {
            return;
        }

        Optional<RefreshToken> registro = refreshTokenRepository.findByJti(jti);
        registro.ifPresent(rt -> {
            rt.setRevocado(true);
            refreshTokenRepository.save(rt);
        });
    }
}

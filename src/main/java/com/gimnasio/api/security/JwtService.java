package com.gimnasio.api.security;

import com.gimnasio.api.models.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Encargado de generar y validar los JWT utilizados para autenticar a los usuarios
 * en cada request, una vez iniciada la sesión.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionMs;

    public JwtService(@Value("${jwt.secret}") String secretoBase64,
                       @Value("${jwt.expiration-ms}") long expiracionMs) {
        this.clave = Keys.hmacShaKeyFor(secretoBase64.getBytes());
        this.expiracionMs = expiracionMs;
    }

    /**
     * Genera un token firmado con el nombre de usuario como subject y su id/rol como claims.
     */
    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expiracionMs);

        return Jwts.builder()
                .subject(usuario.getNombre())
                .claim("id", usuario.getId())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(clave)
                .compact();
    }

    /**
     * Valida la firma y vigencia del token, devolviendo sus claims si es válido.
     * @throws io.jsonwebtoken.JwtException si el token es inválido, está corrupto o expiró.
     */
    public Claims validarYObtenerClaims(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extraerNombreUsuario(String token) {
        return validarYObtenerClaims(token).getSubject();
    }

    public String extraerRol(String token) {
        return validarYObtenerClaims(token).get("rol", String.class);
    }
}

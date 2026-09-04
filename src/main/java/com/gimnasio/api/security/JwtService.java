package com.gimnasio.api.security;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Encargado de generar y validar los JWT utilizados para autenticar a los usuarios:
 * access tokens de corta duración (enviados en el body) y refresh tokens de larga
 * duración (enviados solo en cookie HttpOnly).
 */
@Service
public class JwtService {

    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_REFRESH = "refresh";
    private static final String ROL_CLIENTE = "CLIENTE";

    private final SecretKey clave;
    private final long accessExpiracionMs;
    private final long refreshExpiracionMs;

    public JwtService(@Value("${jwt.secret}") String secretoBase64,
                       @Value("${jwt.access-expiration-ms}") long accessExpiracionMs,
                       @Value("${jwt.refresh-expiration-ms}") long refreshExpiracionMs) {
        this.clave = Keys.hmacShaKeyFor(secretoBase64.getBytes());
        this.accessExpiracionMs = accessExpiracionMs;
        this.refreshExpiracionMs = refreshExpiracionMs;
    }

    /**
     * Genera un access token de corta duración con el nombre de usuario como subject
     * y su id/rol como claims. Se devuelve al cliente en el cuerpo de la respuesta.
     */
    public String generarAccessToken(Usuario usuario) {
        return generarAccessToken(usuario.getId(), usuario.getNombre(), usuario.getRol().name());
    }

    /**
     * Genera un access token para un Cliente autenticado en el portal web.
     * El subject es el email (su identificador de login) y el rol queda fijo en "CLIENTE",
     * un rol distinto al de Usuario/staff (ver RolUsuario) que nunca se mezcla con ese enum.
     */
    public String generarAccessTokenCliente(Cliente cliente) {
        return generarAccessToken(cliente.getId(), cliente.getEmail(), ROL_CLIENTE);
    }

    private String generarAccessToken(Integer id, String subject, String rol) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + accessExpiracionMs);

        return Jwts.builder()
                .subject(subject)
                .claim("id", id)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(clave)
                .compact();
    }

    /**
     * Genera un refresh token de larga duración identificado por un jti único,
     * que el llamador debe persistir para poder validarlo o revocarlo luego.
     */
    public String generarRefreshToken(Usuario usuario, String jti) {
        return generarRefreshToken(usuario.getNombre(), jti);
    }

    public String generarRefreshTokenCliente(Cliente cliente, String jti) {
        return generarRefreshToken(cliente.getEmail(), jti);
    }

    private String generarRefreshToken(String subject, String jti) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + refreshExpiracionMs);

        return Jwts.builder()
                .id(jti)
                .subject(subject)
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(clave)
                .compact();
    }

    public String generarJti() {
        return UUID.randomUUID().toString();
    }

    public long getRefreshExpiracionMs() {
        return refreshExpiracionMs;
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

    public String extraerJti(String token) {
        return validarYObtenerClaims(token).getId();
    }

    public boolean esRefreshToken(String token) {
        return TIPO_REFRESH.equals(validarYObtenerClaims(token).get(CLAIM_TIPO, String.class));
    }
}

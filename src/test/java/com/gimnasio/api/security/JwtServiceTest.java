package com.gimnasio.api.security;

import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRETO_BASE64 = "Y2FtYmlhLWVzdGEtY2xhdmUtc2VjcmV0YS1lbi1wcm9kdWNjaW9uLW1pbmltby0yNTYtYml0cw==";

    private JwtService jwtService;
    private Usuario usuarioAdmin;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRETO_BASE64, 1800000L, 2592000000L);
        usuarioAdmin = new Usuario(1, "admin", "hash-irrelevante", RolUsuario.ADMIN);
    }

    @Test
    @DisplayName("El access token generado debe contener el nombre de usuario y el rol correctos")
    void generarAccessToken_deberiaContenerNombreYRol() {
        String token = jwtService.generarAccessToken(usuarioAdmin);

        assertNotNull(token);
        assertEquals("admin", jwtService.extraerNombreUsuario(token));
        assertEquals("ADMIN", jwtService.extraerRol(token));
    }

    @Test
    @DisplayName("Un access token expirado debe lanzar ExpiredJwtException al validarlo")
    void tokenExpirado_deberiaLanzarExcepcion() {
        JwtService servicioConExpiracionInmediata = new JwtService(SECRETO_BASE64, -1000L, 2592000000L);
        String tokenExpirado = servicioConExpiracionInmediata.generarAccessToken(usuarioAdmin);

        assertThrows(ExpiredJwtException.class, () -> jwtService.validarYObtenerClaims(tokenExpirado));
    }

    @Test
    @DisplayName("Un token firmado con otra clave debe ser rechazado")
    void tokenConFirmaInvalida_deberiaSerRechazado() {
        JwtService servicioConOtraClave = new JwtService(
                "b3RyYS1jbGF2ZS1jb21wbGV0YW1lbnRlLWRpc3RpbnRhLWRlLTI1Ni1iaXRz", 1800000L, 2592000000L);
        String tokenConOtraFirma = servicioConOtraClave.generarAccessToken(usuarioAdmin);

        assertThrows(SignatureException.class, () -> jwtService.validarYObtenerClaims(tokenConOtraFirma));
    }

    @Test
    @DisplayName("El refresh token generado debe llevar jti y marcarse como tipo refresh")
    void generarRefreshToken_deberiaContenerJtiYTipoRefresh() {
        String jti = jwtService.generarJti();
        String token = jwtService.generarRefreshToken(usuarioAdmin, jti);

        assertEquals(jti, jwtService.extraerJti(token));
        assertTrue(jwtService.esRefreshToken(token));
    }

    @Test
    @DisplayName("Un access token no debe considerarse refresh token")
    void accessToken_noDeberiaSerConsideradoRefreshToken() {
        String token = jwtService.generarAccessToken(usuarioAdmin);

        assertFalse(jwtService.esRefreshToken(token));
    }
}

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
        jwtService = new JwtService(SECRETO_BASE64, 86400000L);
        usuarioAdmin = new Usuario(1, "admin", "hash-irrelevante", RolUsuario.ADMIN);
    }

    @Test
    @DisplayName("El token generado debe contener el nombre de usuario y el rol correctos")
    void generarToken_deberiaContenerNombreYRol() {
        String token = jwtService.generarToken(usuarioAdmin);

        assertNotNull(token);
        assertEquals("admin", jwtService.extraerNombreUsuario(token));
        assertEquals("ADMIN", jwtService.extraerRol(token));
    }

    @Test
    @DisplayName("Un token expirado debe lanzar ExpiredJwtException al validarlo")
    void tokenExpirado_deberiaLanzarExcepcion() {
        JwtService servicioConExpiracionInmediata = new JwtService(SECRETO_BASE64, -1000L);
        String tokenExpirado = servicioConExpiracionInmediata.generarToken(usuarioAdmin);

        assertThrows(ExpiredJwtException.class, () -> jwtService.validarYObtenerClaims(tokenExpirado));
    }

    @Test
    @DisplayName("Un token firmado con otra clave debe ser rechazado")
    void tokenConFirmaInvalida_deberiaSerRechazado() {
        JwtService servicioConOtraClave = new JwtService(
                "b3RyYS1jbGF2ZS1jb21wbGV0YW1lbnRlLWRpc3RpbnRhLWRlLTI1Ni1iaXRz", 86400000L);
        String tokenConOtraFirma = servicioConOtraClave.generarToken(usuarioAdmin);

        assertThrows(SignatureException.class, () -> jwtService.validarYObtenerClaims(tokenConOtraFirma));
    }
}

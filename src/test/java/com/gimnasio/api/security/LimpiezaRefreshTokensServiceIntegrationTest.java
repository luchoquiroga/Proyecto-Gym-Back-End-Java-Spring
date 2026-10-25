package com.gimnasio.api.security;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.ClienteRefreshToken;
import com.gimnasio.api.models.RefreshToken;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.ClienteRefreshTokenRepository;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.RefreshTokenRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La limpieza diaria contra la base de verdad: lo que importa es qué filas borra el DELETE,
 * y eso no se ve con mocks.
 */
@SpringBootTest
@Transactional
class LimpiezaRefreshTokensServiceIntegrationTest {

    @Autowired
    private LimpiezaRefreshTokensService limpieza;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private ClienteRefreshTokenRepository clienteRefreshTokenRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ClienteRepository clienteRepository;

    private final Instant ahora = Instant.now();

    @Test
    void borraSoloLosVencidosHaceMasDeUnaSemana_deLosDosPortales() {
        Usuario usuario = usuarioRepository.save(
                new Usuario(null, "limpieza-" + UUID.randomUUID(), "sin-uso", RolUsuario.GERENCIA));
        String documento = "LT" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Cliente cliente = clienteRepository.save(new Cliente(null, "Limpieza", "Tokens", null,
                documento, null, null, EstadoCliente.INACTIVO, null));

        String staffViejo = tokenDeStaff(usuario, haceDias(8), false);
        String staffRecienVencido = tokenDeStaff(usuario, haceDias(2), false);
        String staffVigenteRevocado = tokenDeStaff(usuario, ahora.plus(Duration.ofDays(5)), true);
        String socioViejo = tokenDeSocio(cliente, haceDias(30));
        String socioVigente = tokenDeSocio(cliente, ahora.plus(Duration.ofDays(5)));

        limpieza.borrarVencidos();

        assertThat(refreshTokenRepository.findByJti(staffViejo)).isEmpty();
        assertThat(clienteRefreshTokenRepository.findByJti(socioViejo)).isEmpty();
        // Dentro del margen de una semana, o todavía vigentes: se quedan.
        assertThat(refreshTokenRepository.findByJti(staffRecienVencido)).isPresent();
        assertThat(refreshTokenRepository.findByJti(staffVigenteRevocado)).isPresent();
        assertThat(clienteRefreshTokenRepository.findByJti(socioVigente)).isPresent();
    }

    private Instant haceDias(int dias) {
        return ahora.minus(Duration.ofDays(dias));
    }

    private String tokenDeStaff(Usuario usuario, Instant expiracion, boolean revocado) {
        String jti = UUID.randomUUID().toString();
        refreshTokenRepository.saveAndFlush(new RefreshToken(null, jti, usuario, expiracion, revocado));
        return jti;
    }

    private String tokenDeSocio(Cliente cliente, Instant expiracion) {
        String jti = UUID.randomUUID().toString();
        clienteRefreshTokenRepository.saveAndFlush(new ClienteRefreshToken(null, jti, cliente, expiracion, false));
        return jti;
    }
}

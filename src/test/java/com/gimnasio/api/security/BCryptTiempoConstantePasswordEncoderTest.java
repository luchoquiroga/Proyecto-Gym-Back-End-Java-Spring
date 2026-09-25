package com.gimnasio.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BCryptTiempoConstantePasswordEncoderTest {

    private final BCryptTiempoConstantePasswordEncoder encoder = new BCryptTiempoConstantePasswordEncoder();

    @Test
    @DisplayName("Con el hash correcto sigue funcionando como BCrypt")
    void matches_conHashReal_comparaNormalmente() {
        String hash = encoder.encode("claveCorrecta123");

        assertTrue(encoder.matches("claveCorrecta123", hash));
        assertFalse(encoder.matches("otraClave123", hash));
    }

    @Test
    @DisplayName("Sin hash (cuenta inexistente) devuelve false, incluso con la clave del relleno")
    void matches_sinHash_devuelveFalse() {
        assertFalse(encoder.matches("relleno-para-cuentas-inexistentes", null));
        assertFalse(encoder.matches("cualquiera", ""));
    }

    @Test
    @DisplayName("Sin hash tarda lo mismo que con uno: corre BCrypt contra el relleno")
    void matches_sinHash_correBCryptIgual() {
        String hash = encoder.encode("claveCorrecta123");
        encoder.matches("calentamiento", hash);

        long conHash = medirNanos(() -> encoder.matches("otraClave123", hash));
        long sinHash = medirNanos(() -> encoder.matches("otraClave123", null));

        // BCrypt con costo 10 tarda decenas de milisegundos; volver en el acto tarda
        // microsegundos. No se compara fino para que el test no dependa de la máquina:
        // alcanza con ver que el caso sin hash no es órdenes de magnitud más rápido.
        assertTrue(sinHash > conHash / 4,
                "sin hash tardó " + sinHash / 1_000_000 + " ms, con hash " + conHash / 1_000_000 + " ms");
    }

    private long medirNanos(Runnable accion) {
        long inicio = System.nanoTime();
        accion.run();
        return System.nanoTime() - inicio;
    }
}

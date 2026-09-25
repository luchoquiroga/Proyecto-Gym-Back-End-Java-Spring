package com.gimnasio.api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt que tarda lo mismo cuando no hay contra qué comparar.
 *
 * <p>El login busca la cuenta y recién después corre BCrypt (~100 ms). Si la cuenta no
 * existía, el {@code matches} con un hash nulo volvía en el acto, así que midiendo el tiempo
 * de respuesta se podía saber qué nombres de staff o emails de socios existen, aunque el
 * mensaje de error sea el mismo. Acá un hash nulo o vacío se compara contra un hash de
 * relleno y se devuelve {@code false} igual: el trabajo es el mismo, el resultado no cambia.
 *
 * <p>El relleno se genera con este mismo encoder, así que tiene siempre el mismo costo que
 * los hashes reales: si alguien sube la fuerza de BCrypt, el relleno la sigue solo.
 */
public class BCryptTiempoConstantePasswordEncoder implements PasswordEncoder {

    // Composición y no herencia: en Spring Security 7 el matches de BCryptPasswordEncoder es
    // final y ya resuelve el hash nulo devolviendo false en el acto, que es lo que se evita.
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final String hashDeRelleno = bcrypt.encode("relleno-para-cuentas-inexistentes");

    @Override
    public String encode(CharSequence contrasena) {
        return bcrypt.encode(contrasena);
    }

    @Override
    public boolean matches(CharSequence contrasena, String hashGuardado) {
        if (hashGuardado == null || hashGuardado.isEmpty()) {
            bcrypt.matches(contrasena, hashDeRelleno);
            return false;
        }
        return bcrypt.matches(contrasena, hashGuardado);
    }

    @Override
    public boolean upgradeEncoding(String hashGuardado) {
        return bcrypt.upgradeEncoding(hashGuardado);
    }
}

package com.gimnasio.api.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cómo se maneja la plata: {@link BigDecimal} con dos decimales, igual que la columna
 * NUMERIC(12,2) de la base (V9).
 *
 * Hasta la V9 era {@code Double}, que guarda los decimales de forma aproximada (0,1 + 0,2
 * da 0,30000000000000004): sumar muchos cobros con centavos podía correr el total del mes.
 *
 * Dos trampas de BigDecimal que este proyecto evita así:
 * <ul>
 *   <li>{@code equals} compara también la escala: 35000 y 35000.00 son "distintos". Para
 *       comparar montos se usa siempre {@code compareTo}.</li>
 *   <li>La escala viaja al JSON: sin normalizar, el mismo precio sale como {@code 35000}
 *       recién cargado y como {@code 35000.00} leído de la base. Las respuestas pasan por
 *       {@link #normalizar} para que el front reciba siempre la misma forma.</li>
 * </ul>
 */
public final class Plata {

    public static final int DECIMALES = 2;

    private Plata() {
    }

    /**
     * Lleva un monto a dos decimales. No redondea: los DTO ya rechazan más de dos decimales
     * con {@code @Digits}, así que si llega uno acá es un bug y tiene que fallar fuerte.
     */
    public static BigDecimal normalizar(BigDecimal monto) {
        return monto == null ? null : monto.setScale(DECIMALES, RoundingMode.UNNECESSARY);
    }
}

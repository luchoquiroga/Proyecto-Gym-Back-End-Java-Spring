package com.gimnasio.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Define el "hoy" del negocio. El gimnasio opera en Argentina, pero la JVM en Render
 * corre en UTC: sin esto, desde las 21:00 el backend ya vive en el día siguiente (un
 * cobro queda con fecha de mañana y la corrida de vencimientos pasa a MOROSO a quien
 * todavía tiene tres horas de su último día).
 *
 * Todo lo que necesite la fecha actual recibe este {@link Clock} en vez de llamar a
 * {@code LocalDate.now()}: así la zona se fija en un solo lugar y los tests pueden
 * usar un reloj fijo para probar, por ejemplo, qué pasa a las 22:00.
 */
@Configuration
public class ZonaHorariaConfig {

    /** Constante y no solo el bean porque {@code @Scheduled(zone = ...)} exige un literal. */
    public static final String ZONA_GIMNASIO = "America/Argentina/Buenos_Aires";

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(ZONA_GIMNASIO));
    }
}

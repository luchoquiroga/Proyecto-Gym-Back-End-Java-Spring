package com.gimnasio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO con la cantidad actual de socios en cada estado, para el dashboard del administrador.
 * Es una foto de hoy, no de un período: por eso vive aparte de {@link GananciasMensualesResponse},
 * que responde por el mes que se le pida.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SociosPorEstadoResponse {

    private long activos;
    private long morosos;
    private long inactivos;
}

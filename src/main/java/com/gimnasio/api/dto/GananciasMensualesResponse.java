package com.gimnasio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO con el resumen de ganancias de un mes puntual, para el dashboard del administrador.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GananciasMensualesResponse {

    private int anio;
    private int mes;
    private double totalGanancias;
    private long cantidadPagos;
}

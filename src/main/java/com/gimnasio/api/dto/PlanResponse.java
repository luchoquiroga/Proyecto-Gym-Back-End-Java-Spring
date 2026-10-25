package com.gimnasio.api.dto;

import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Plata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Datos públicos de un Plan, seguros para exponer en respuestas del catálogo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {

    private Integer id;
    private String nombre;
    private BigDecimal precio;
    private Integer duracion;

    public static PlanResponse desde(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getNombre(), Plata.normalizar(plan.getPrecio()), plan.getDuracion());
    }
}

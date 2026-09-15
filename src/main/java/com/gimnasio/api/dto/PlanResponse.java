package com.gimnasio.api.dto;

import com.gimnasio.api.models.Plan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos públicos de un Plan, seguros para exponer en respuestas del catálogo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {

    private Integer id;
    private String nombre;
    private Double precio;
    private Integer duracion;

    public static PlanResponse desde(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getNombre(), plan.getPrecio(), plan.getDuracion());
    }
}

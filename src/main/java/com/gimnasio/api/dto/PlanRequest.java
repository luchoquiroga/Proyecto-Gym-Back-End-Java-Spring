package com.gimnasio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para alta/edición de un Plan. Sin campo id: el alta nunca debe poder
 * pisar otra fila del catálogo vía un id enviado en el body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanRequest {

    @NotBlank(message = "El nombre del plan es obligatorio")
    private String nombre;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a cero")
    private Double precio;

    @NotNull(message = "La duración es obligatoria")
    @Positive(message = "La duración debe ser mayor a cero")
    private Integer duracion;
}

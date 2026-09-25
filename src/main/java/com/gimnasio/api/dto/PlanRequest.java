package com.gimnasio.api.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
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
    @Size(max = 100, message = "El nombre del plan no puede superar los 100 caracteres")
    private String nombre;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a cero")
    // Tope de cordura, no de negocio: sin él, un 1e400 en el JSON llega como Infinity, la
    // columna DOUBLE PRECISION lo acepta y ningún pago vuelve a alcanzar el precio del plan.
    @DecimalMax(value = "1000000000", message = "El precio no puede superar los 1.000.000.000")
    private Double precio;

    @NotNull(message = "La duración es obligatoria")
    @Positive(message = "La duración debe ser mayor a cero")
    // Diez años. Sin tope, una duración enorme da un vencimiento fuera del rango de fechas
    // de Postgres y el primer cobro de ese plan termina en un 500.
    @Max(value = 3660, message = "La duración no puede superar los 3660 días")
    private Integer duracion;
}

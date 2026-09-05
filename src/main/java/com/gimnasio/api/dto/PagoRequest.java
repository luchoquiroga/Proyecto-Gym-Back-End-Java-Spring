package com.gimnasio.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para la solicitud de registro de un nuevo pago.
 * Desacopla la API de la entidad y evita manipulación indebida de campos calculados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoRequest {

    @NotNull(message = "El cliente es obligatorio")
    private Integer clienteId;

    @NotNull(message = "El plan es obligatorio")
    private Integer planId;

    @Positive(message = "El monto abonado debe ser mayor a cero")
    private Double montoAbonado;

    private LocalDate fechaPago;
}

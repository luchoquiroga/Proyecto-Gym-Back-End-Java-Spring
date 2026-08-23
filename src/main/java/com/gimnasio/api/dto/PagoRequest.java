package com.gimnasio.api.dto;

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

    private Integer clienteId;
    private Integer planId;
    private Double montoAbonado;
    private LocalDate fechaPago;
}

package com.gimnasio.api.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación Muchos a Uno: Muchos pagos pueden pertenecer a un solo cliente
    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // Relación Muchos a Uno: Muchos pagos pueden ser de la misma membresía
    @ManyToOne(optional = false)
    @JoinColumn(name = "membresia_id")
    private Membresia membresia;

    @Column(nullable = false)
    private Double montoAbonado;

    @Column(nullable = false)
    private LocalDate fechaPago;

    @Column(nullable = false)
    private LocalDate fechaVencimiento;
}
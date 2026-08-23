package com.gimnasio.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Clave Foránea (FK) hacia Cliente: Muchos pagos pueden pertenecer a un cliente
    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // Clave Foránea (FK) hacia Plan: Muchos pagos pueden asociarse a un mismo plan
    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "monto_abonado", nullable = false)
    private Double montoAbonado;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;
}
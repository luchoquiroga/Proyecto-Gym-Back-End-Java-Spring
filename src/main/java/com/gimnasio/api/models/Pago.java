package com.gimnasio.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * Usuario de staff que registró el cobro. Es el rastro de auditoría de la caja:
     * sin esto, un faltante o un pago cargado de favor no son atribuibles a nadie.
     * Se toma siempre del token del que llama, nunca del body de la request.
     *
     * Nullable porque los pagos anteriores a la migración V3 no tienen autor conocido,
     * y porque al eliminar un usuario de staff la FK lo pone en NULL en vez de borrar
     * el pago.
     *
     * @JsonIgnore porque hoy los controllers serializan la entidad directamente y
     * GET /pagos/{id} lo puede leer el propio CLIENTE dueño del pago: no hay motivo
     * para contarle a un socio qué empleado le cobró. Cuando la Fase 3 del replanteo
     * introduzca DTOs de response, este dato se expone en el de ADMIN.
     */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;
}
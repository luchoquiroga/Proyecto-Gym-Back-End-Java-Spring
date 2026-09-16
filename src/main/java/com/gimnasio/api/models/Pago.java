package com.gimnasio.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /**
     * Un pago anulado es uno cargado por error: la fila se conserva —es el registro de que
     * alguien se equivocó, y de quién— pero deja de contar para todo lo demás. No suma a las
     * ganancias y no cuenta para la fecha de vencimiento del socio.
     *
     * No existe borrar un pago ni editarle el monto: corregir un importe es anular este y
     * registrar el correcto. Ver la migración V6 para el detalle de por qué.
     */
    @Column(nullable = false)
    private boolean anulado = false;

    /** Quién anuló. Sale del token del que llama, nunca del body. */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "anulado_por")
    private Usuario anuladoPor;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    /**
     * Por qué se anuló. Es obligatorio a nivel de negocio: un pago anulado sin explicación
     * no sirve como auditoría, que es el único motivo por el que se conserva la fila.
     */
    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    /**
     * Un pago recién registrado nunca nace anulado: anular es un hecho posterior, con su
     * autor y su motivo. Este constructor existe para no obligar a cada llamador a repetir
     * los cuatro campos de anulación vacíos, y para que no se puedan fijar desde un alta.
     */
    public Pago(Integer id, Cliente cliente, Plan plan, Double montoAbonado,
                LocalDate fechaPago, LocalDate fechaVencimiento, Usuario registradoPor) {
        this(id, cliente, plan, montoAbonado, fechaPago, fechaVencimiento, registradoPor,
                false, null, null, null);
    }
}
package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Pago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByClienteId(Integer clienteId);

    // El socio de un pago, sin cargar el pago. La anulación bloquea al socio antes de leer el
    // pago: si lo leyera primero, se quedaría con la copia de antes del bloqueo.
    @Query("SELECT p.cliente.id FROM Pago p WHERE p.id = :id")
    Optional<Integer> findClienteIdDelPago(@Param("id") Integer id);

    // Último pago de UN solo cliente (mayor fecha de vencimiento). Pensado para los casos
    // donde solo hace falta la fecha de vencimiento vigente de un socio puntual (GET /clientes/{id}
    // y /clientes/buscar): una sola fila, sin necesidad de traer ni mapear el resto de la tabla.
    Optional<Pago> findTopByClienteIdAndAnuladoFalseOrderByFechaVencimientoDesc(Integer clienteId);

    // Trae el pago más reciente (mayor fecha de vencimiento) de cada cliente en una sola consulta
    // Los pagos anulados quedan fuera en los dos niveles de la consulta: ni son candidatos
    // a "último pago" ni participan del MAX que lo define. Si solo se filtrara afuera, un
    // pago anulado con la fecha más alta haría que el subquery eligiera una fecha que
    // después no matchea ninguna fila, y el socio se quedaría sin vencimiento.
    @Query("SELECT p FROM Pago p WHERE p.anulado = false AND p.fechaVencimiento = " +
            "(SELECT MAX(p2.fechaVencimiento) FROM Pago p2 WHERE p2.cliente = p.cliente AND p2.anulado = false)")
    List<Pago> findUltimoPagoPorCadaCliente();

    // Hasta cuándo estaba cubierto el socio a una fecha dada, para encadenar un cobro
    // anticipado al período vigente. Mira solo los pagos cobrados hasta esa fecha: así un
    // pago retroactivo se calcula como si se hubiera cargado a tiempo, sin engancharse a
    // una cobertura que en ese momento todavía no existía.
    @Query("SELECT MAX(p.fechaVencimiento) FROM Pago p " +
            "WHERE p.cliente.id = :clienteId AND p.anulado = false AND p.fechaPago <= :fecha")
    Optional<LocalDate> findVencimientoVigenteAl(@Param("clienteId") Integer clienteId,
                                                 @Param("fecha") LocalDate fecha);

    // Pagos válidos del mismo socio que pudieron encadenarse a este: registrados después
    // (id mayor: los ids son secuenciales) y cobrados mientras este estaba vigente. Anular
    // este les dejaría días que nadie pagó. Un pago registrado antes no pudo encadenarse a
    // uno que todavía no existía, aunque tenga fecha de cobro posterior.
    @Query("SELECT p FROM Pago p WHERE p.cliente.id = :clienteId AND p.anulado = false " +
            "AND p.id > :pagoId AND p.fechaPago >= :desde AND p.fechaPago < :hasta " +
            "ORDER BY p.fechaPago, p.id")
    List<Pago> findCobradosDuranteElPeriodo(@Param("clienteId") Integer clienteId,
                                            @Param("pagoId") Integer pagoId,
                                            @Param("desde") LocalDate desde,
                                            @Param("hasta") LocalDate hasta);

    // Suma total de lo abonado en un rango de fechas de pago (para reportes de ganancias)
    // Las dos consultas del dashboard ignoran los anulados: un pago anulado no es plata que
    // entró, así que no puede sumar al total del mes ni contar como una operación.
    @Query("SELECT COALESCE(SUM(p.montoAbonado), 0) FROM Pago p " +
            "WHERE p.anulado = false AND p.fechaPago BETWEEN :inicio AND :fin")
    Double sumarMontoAbonadoEntre(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    long countByFechaPagoBetweenAndAnuladoFalse(LocalDate inicio, LocalDate fin);

    // Listado acotado por fecha de cobro, para el desglose de ganancias de un mes: el total
    // agregado lo da /dashboard, y estas consultas dan las filas que lo componen. Son tres
    // métodos y no una consulta con parámetros nulos porque cada combinación de filtros es
    // una consulta distinta y explícita; el service elige cuál según qué mandó el llamador.
    Page<Pago> findByFechaPagoBetween(LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Pago> findByFechaPagoGreaterThanEqual(LocalDate desde, Pageable pageable);

    Page<Pago> findByFechaPagoLessThanEqual(LocalDate hasta, Pageable pageable);
}

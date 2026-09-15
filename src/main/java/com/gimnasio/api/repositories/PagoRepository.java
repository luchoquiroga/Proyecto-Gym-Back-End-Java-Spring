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

    // Permite buscar pagos por coincidencia parcial de nombre de cliente (case-insensitive)
    List<Pago> findByClienteNombreContainingIgnoreCase(String nombre);

    // Último pago de UN solo cliente (mayor fecha de vencimiento). Pensado para los casos
    // donde solo hace falta la fecha de vencimiento vigente de un socio puntual (GET /clientes/{id}
    // y /clientes/buscar): una sola fila, sin necesidad de traer ni mapear el resto de la tabla.
    Optional<Pago> findTopByClienteIdOrderByFechaVencimientoDesc(Integer clienteId);

    // Trae el pago más reciente (mayor fecha de vencimiento) de cada cliente en una sola consulta
    @Query("SELECT p FROM Pago p WHERE p.fechaVencimiento = " +
            "(SELECT MAX(p2.fechaVencimiento) FROM Pago p2 WHERE p2.cliente = p.cliente)")
    List<Pago> findUltimoPagoPorCadaCliente();

    // Suma total de lo abonado en un rango de fechas de pago (para reportes de ganancias)
    @Query("SELECT COALESCE(SUM(p.montoAbonado), 0) FROM Pago p WHERE p.fechaPago BETWEEN :inicio AND :fin")
    Double sumarMontoAbonadoEntre(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    long countByFechaPagoBetween(LocalDate inicio, LocalDate fin);

    // Listado acotado por fecha de cobro, para el desglose de ganancias de un mes: el total
    // agregado lo da /dashboard, y estas consultas dan las filas que lo componen. Son tres
    // métodos y no una consulta con parámetros nulos porque cada combinación de filtros es
    // una consulta distinta y explícita; el service elige cuál según qué mandó el llamador.
    Page<Pago> findByFechaPagoBetween(LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Pago> findByFechaPagoGreaterThanEqual(LocalDate desde, Pageable pageable);

    Page<Pago> findByFechaPagoLessThanEqual(LocalDate hasta, Pageable pageable);
}

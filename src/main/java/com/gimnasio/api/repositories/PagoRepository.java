package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByClienteId(Integer clienteId);

    // Permite buscar pagos por coincidencia parcial de nombre de cliente (case-insensitive)
    List<Pago> findByClienteNombreContainingIgnoreCase(String nombre);

    // Trae el pago más reciente (mayor fecha de vencimiento) de cada cliente en una sola consulta
    @Query("SELECT p FROM Pago p WHERE p.fechaVencimiento = " +
            "(SELECT MAX(p2.fechaVencimiento) FROM Pago p2 WHERE p2.cliente = p.cliente)")
    List<Pago> findUltimoPagoPorCadaCliente();

    // Suma total de lo abonado en un rango de fechas de pago (para reportes de ganancias)
    @Query("SELECT COALESCE(SUM(p.montoAbonado), 0) FROM Pago p WHERE p.fechaPago BETWEEN :inicio AND :fin")
    Double sumarMontoAbonadoEntre(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    long countByFechaPagoBetween(LocalDate inicio, LocalDate fin);
}

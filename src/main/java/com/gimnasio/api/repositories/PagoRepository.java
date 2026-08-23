package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}

package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByClienteId(Integer clienteId);

    // Permite buscar pagos por coincidencia parcial de nombre de cliente (case-insensitive)
    List<Pago> findByClienteNombreContainingIgnoreCase(String nombre);
}

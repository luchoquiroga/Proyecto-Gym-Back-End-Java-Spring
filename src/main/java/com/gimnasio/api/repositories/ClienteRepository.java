package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {
    // Búsqueda parcial e insensible a mayúsculas, para alimentar el buscador del
    // mostrador: exigir el nombre exacto no sirve para escribir y filtrar.
    List<Cliente> findByNombreContainingIgnoreCase(String nombre);
    // Ignorando mayúsculas: el email se guarda en minúsculas desde el 2026-09-25, pero una
    // fila cargada antes puede tenerlas, y ese socio tiene que poder seguir entrando.
    Optional<Cliente> findByEmailIgnoreCase(String email);
    Optional<Cliente> findByDocumento(String documento);
    Optional<Cliente> findByCodigoActivacion(String codigoActivacion);
    boolean existsByCodigoActivacion(String codigoActivacion);
    long countByEstado(EstadoCliente estado);

    // Lee al socio bloqueando su fila (SELECT ... FOR UPDATE) hasta que termine la
    // transacción. Lo usan el cobro y la anulación: los dos leen el vencimiento vigente y
    // escriben encima, así que dos a la vez sobre el mismo socio (un doble clic en "Cobrar")
    // calculaban ambos desde el mismo vencimiento y el socio pagaba dos veces un solo período.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.id = :id")
    Optional<Cliente> findByIdParaActualizar(@Param("id") Integer id);
}

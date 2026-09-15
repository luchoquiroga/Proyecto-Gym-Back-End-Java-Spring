package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {
    // Búsqueda parcial e insensible a mayúsculas, para alimentar el buscador del
    // mostrador: exigir el nombre exacto no sirve para escribir y filtrar.
    List<Cliente> findByNombreContainingIgnoreCase(String nombre);
    Optional<Cliente> findByEmail(String email);
    Optional<Cliente> findByCodigoActivacion(String codigoActivacion);
    boolean existsByCodigoActivacion(String codigoActivacion);
}

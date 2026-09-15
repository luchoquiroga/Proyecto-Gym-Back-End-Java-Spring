package com.gimnasio.api.repositories;

import com.gimnasio.api.models.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Integer> {
    // Búsqueda parcial e insensible a mayúsculas, para alimentar un buscador de UI:
    // devuelve todas las coincidencias en vez de exigir el nombre exacto.
    List<Plan> findByNombreContainingIgnoreCase(String nombre);
}

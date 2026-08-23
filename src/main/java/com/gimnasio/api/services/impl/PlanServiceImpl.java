package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Plan;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.services.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación de la lógica de negocio para la gestión de Planes.
 */
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Plan> obtenerTodos() {
        return planRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Plan obtenerPorId(Integer id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Plan buscarPorNombre(String nombre) {
        return planRepository.findByNombre(nombre)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado con el nombre: " + nombre));
    }

    @Override
    @Transactional
    public Plan crear(Plan plan) {
        validarPlan(plan);
        return planRepository.save(plan);
    }

    @Override
    @Transactional
    public Plan actualizar(Integer id, Plan planActualizado) {
        validarPlan(planActualizado);
        Plan planExistente = obtenerPorId(id);

        planExistente.setNombre(planActualizado.getNombre());
        planExistente.setPrecio(planActualizado.getPrecio());
        planExistente.setDuracion(planActualizado.getDuracion());

        return planRepository.save(planExistente);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Plan plan = obtenerPorId(id);
        try {
            planRepository.delete(plan);
            planRepository.flush(); // Fuerza la ejecución del DELETE para capturar violación de FK de inmediato
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se puede eliminar el plan '" + plan.getNombre() + 
                    "' porque ya existen pagos registrados asociados a él.");
        }
    }

    private void validarPlan(Plan plan) {
        if (plan.getNombre() == null || plan.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del plan es obligatorio.");
        }
        if (plan.getPrecio() == null || plan.getPrecio() < 0) {
            throw new IllegalArgumentException("El precio del plan no puede ser negativo ni nulo.");
        }
        if (plan.getDuracion() == null || plan.getDuracion() <= 0) {
            throw new IllegalArgumentException("La duración del plan debe ser de al menos 1 día.");
        }
    }
}

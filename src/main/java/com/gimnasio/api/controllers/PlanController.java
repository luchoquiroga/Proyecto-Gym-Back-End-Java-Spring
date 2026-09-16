package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.PlanRequest;
import com.gimnasio.api.dto.PlanResponse;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.services.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST versionado para el catálogo de Planes/Membresías.
 */
@RestController
@RequestMapping("/api/v1/planes")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> obtenerTodos() {
        List<PlanResponse> planes = planService.obtenerTodos().stream()
                .map(PlanResponse::desde)
                .toList();
        return ResponseEntity.ok(planes);
    }

    @PostMapping
    public ResponseEntity<PlanResponse> crear(@Valid @RequestBody PlanRequest request) {
        Plan plan = new Plan(null, request.getNombre(), request.getPrecio(), request.getDuracion());
        Plan nuevoPlan = planService.crear(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanResponse.desde(nuevoPlan));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody PlanRequest request) {
        Plan plan = new Plan(null, request.getNombre(), request.getPrecio(), request.getDuracion());
        return ResponseEntity.ok(PlanResponse.desde(planService.actualizar(id, plan)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        planService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

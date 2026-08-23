package com.gimnasio.api.controllers;

import com.gimnasio.api.models.Plan;
import com.gimnasio.api.services.PlanService;
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
    public ResponseEntity<List<Plan>> obtenerTodos() {
        return ResponseEntity.ok(planService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Plan> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(planService.obtenerPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<Plan> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(planService.buscarPorNombre(nombre));
    }

    @PostMapping
    public ResponseEntity<Plan> crear(@RequestBody Plan plan) {
        Plan nuevoPlan = planService.crear(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoPlan);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Plan> actualizar(@PathVariable Integer id, @RequestBody Plan plan) {
        return ResponseEntity.ok(planService.actualizar(id, plan));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        planService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.gimnasio.api.services;

import com.gimnasio.api.models.Plan;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.services.impl.PlanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PlanServiceImpl planService;

    private Plan planMensual;

    @BeforeEach
    void setUp() {
        planMensual = new Plan(1, "Pase Mensual", 32500.0, 30);
    }

    @Test
    @DisplayName("Crear plan con datos válidos debe persistirlo")
    void crear_conDatosValidos_deberiaGuardarPlan() {
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Plan nuevo = new Plan(null, "Pase Quincenal", 18000.0, 15);
        Plan resultado = planService.crear(nuevo);

        assertNotNull(resultado);
        assertEquals("Pase Quincenal", resultado.getNombre());
        verify(planRepository, times(1)).save(any(Plan.class));
    }

    @Test
    @DisplayName("Crear plan con precio negativo debe lanzar IllegalArgumentException")
    void crear_conPrecioNegativo_deberiaLanzarExcepcion() {
        Plan invalido = new Plan(null, "Pase Inválido", -500.0, 30);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            planService.crear(invalido);
        });

        assertTrue(ex.getMessage().contains("precio del plan no puede ser negativo"));
        verify(planRepository, never()).save(any(Plan.class));
    }

    @Test
    @DisplayName("Eliminar plan con pagos asociados debe lanzar excepción descriptiva")
    void eliminar_cuandoTienePagos_deberiaLanzarExcepcion() {
        when(planRepository.findById(1)).thenReturn(Optional.of(planMensual));
        doThrow(new DataIntegrityViolationException("FK violation")).when(planRepository).flush();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            planService.eliminar(1);
        });

        assertTrue(ex.getMessage().contains("porque ya existen pagos registrados"));
        verify(planRepository, times(1)).delete(planMensual);
    }
}

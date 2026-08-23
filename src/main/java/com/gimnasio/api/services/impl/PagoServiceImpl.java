package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.services.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementación de la lógica de negocio para la gestión de Pagos.
 */
@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {

    private final PagoRepository pagoRepository;
    private final ClienteRepository clienteRepository;
    private final PlanRepository planRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Pago> obtenerTodos() {
        return pagoRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Pago obtenerPorId(Integer id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pago> obtenerPagosPorCliente(Integer clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new RuntimeException("Cliente no encontrado con ID: " + clienteId);
        }
        return pagoRepository.findByClienteId(clienteId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pago> buscarPagosPorNombreCliente(String nombreCliente) {
        return pagoRepository.findByClienteNombreContainingIgnoreCase(nombreCliente);
    }

    @Override
    @Transactional
    public Pago registrarPago(Integer clienteId, Integer planId, Double montoAbonado, LocalDate fechaPago) {
        // 1. Validar que el cliente exista
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("No se puede registrar el pago: Cliente no encontrado con ID " + clienteId));

        // 2. Validar que el plan exista
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("No se puede registrar el pago: Plan no encontrado con ID " + planId));

        // 3. Establecer fecha de pago por defecto (hoy si no se especifica)
        LocalDate fechaEfectiva = (fechaPago != null) ? fechaPago : LocalDate.now();

        // 4. Establecer monto por defecto (precio del plan si no se especifica)
        Double montoFinal = (montoAbonado != null && montoAbonado > 0) ? montoAbonado : plan.getPrecio();

        // 5. Calcular la fecha de vencimiento sumando la duración en días del plan
        LocalDate fechaVencimiento = fechaEfectiva.plusDays(plan.getDuracion());

        // 6. Crear y guardar la entidad Pago
        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setPlan(plan);
        pago.setMontoAbonado(montoFinal);
        pago.setFechaPago(fechaEfectiva);
        pago.setFechaVencimiento(fechaVencimiento);

        Pago pagoGuardado = pagoRepository.save(pago);

        // 7. Regla de negocio: Activar al cliente al recibir el pago
        cliente.setEstado(EstadoCliente.ACTIVO);
        clienteRepository.save(cliente);

        return pagoGuardado;
    }
}

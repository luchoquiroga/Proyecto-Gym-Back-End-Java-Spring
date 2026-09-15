package com.gimnasio.api.services.impl;

import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import com.gimnasio.api.services.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Pago> obtenerTodos(LocalDate desde, LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }

        if (desde != null && hasta != null) {
            return pagoRepository.findByFechaPagoBetween(desde, hasta, pageable);
        }
        if (desde != null) {
            return pagoRepository.findByFechaPagoGreaterThanEqual(desde, pageable);
        }
        if (hasta != null) {
            return pagoRepository.findByFechaPagoLessThanEqual(hasta, pageable);
        }
        return pagoRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Pago obtenerPorId(Integer id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pago> obtenerPagosPorCliente(Integer clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new RecursoNoEncontradoException("Cliente no encontrado con ID: " + clienteId);
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
    public Pago registrarPago(Integer clienteId, Integer planId, Double montoAbonado, LocalDate fechaPago,
                              Integer registradoPorId) {
        // 1. Validar que el cliente exista
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se puede registrar el pago: Cliente no encontrado con ID " + clienteId));

        // 2. Validar que el plan exista
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se puede registrar el pago: Plan no encontrado con ID " + planId));

        // 3. Identificar al staff que cobra. Llega desde el token (nunca del body), así
        // que si no existe es que el usuario fue eliminado con su sesión todavía viva.
        Usuario registradoPor = usuarioRepository.findById(registradoPorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se puede registrar el pago: Usuario no encontrado con ID " + registradoPorId));

        // 4. Establecer fecha de pago por defecto (hoy si no se especifica)
        LocalDate fechaEfectiva = (fechaPago != null) ? fechaPago : LocalDate.now();

        // 5. Establecer monto: si no se especifica, se cobra el precio oficial del plan.
        Double montoFinal = (montoAbonado != null) ? montoAbonado : plan.getPrecio();

        // 6. Regla de negocio: no existe el pago parcial. Un monto menor al precio del
        // plan se rechaza en vez de registrarse, porque antes cualquier importe (incluso
        // $1) daba de alta un período completo.
        if (montoFinal < plan.getPrecio()) {
            throw new IllegalArgumentException(
                    "El monto abonado (" + montoFinal + ") es menor al precio del plan " + plan.getNombre()
                            + " (" + plan.getPrecio() + "). No se admiten pagos parciales.");
        }

        // 7. Calcular la fecha de vencimiento sumando la duración en días del plan
        LocalDate fechaVencimiento = fechaEfectiva.plusDays(plan.getDuracion());

        // 8. Crear y guardar la entidad Pago
        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setPlan(plan);
        pago.setMontoAbonado(montoFinal);
        pago.setFechaPago(fechaEfectiva);
        pago.setFechaVencimiento(fechaVencimiento);
        pago.setRegistradoPor(registradoPor);

        Pago pagoGuardado = pagoRepository.save(pago);

        // 9. Regla de negocio: el pago activa al cliente, pero solo si efectivamente lo
        // deja al día. Un pago retroactivo cuyo período ya venció no reactiva a nadie:
        // el scheduler de vencimientos solo escala estados (nunca los revierte), así que
        // activar acá a ciegas dejaba al cliente ACTIVO indebidamente.
        if (fechaVencimiento.isAfter(LocalDate.now())) {
            cliente.setEstado(EstadoCliente.ACTIVO);
            clienteRepository.save(cliente);
        }

        return pagoGuardado;
    }
}

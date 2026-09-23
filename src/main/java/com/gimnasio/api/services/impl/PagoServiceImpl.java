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
import com.gimnasio.api.services.VencimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    // Anular el último pago de un socio puede dejarlo con el vencimiento pasado: el estado
    // se recalcula con la misma regla que usa la corrida diaria, en vez de reescribirla acá.
    private final VencimientoService vencimientoService;
    private final Clock clock;

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
    @Transactional
    public Pago anular(Integer id, String motivo, Integer anuladoPorId) {
        Pago pago = obtenerPorId(id);

        if (pago.isAnulado()) {
            // 400 explícito y no una anulación silenciosa: volver a anular casi siempre
            // significa que el operador está mirando una pantalla desactualizada, y
            // pisar el motivo y el autor originales borraría la auditoría del primero.
            throw new IllegalArgumentException("El pago " + id + " ya estaba anulado.");
        }

        // Un cobro anticipado arranca en el vencimiento del pago vigente (ver registrarPago).
        // Si se anulara este pago, el que se encadenó a él conservaría su vencimiento corrido
        // y el socio se quedaría con días que no pagó. No se recalcula el posterior porque un
        // pago registrado no se edita: se pide anularlo primero.
        List<Pago> posteriores = pagoRepository.findCobradosDuranteElPeriodo(
                pago.getCliente().getId(), pago.getId(), pago.getFechaPago(), pago.getFechaVencimiento());
        if (!posteriores.isEmpty()) {
            throw new IllegalArgumentException("No se puede anular el pago " + id
                    + ": el pago " + posteriores.get(0).getId()
                    + " se cobró durante su período y puede estar encadenado a él. Anulá primero ese pago.");
        }

        Usuario anuladoPor = anuladoPorId == null ? null
                : usuarioRepository.findById(anuladoPorId).orElse(null);

        pago.setAnulado(true);
        pago.setAnuladoPor(anuladoPor);
        pago.setFechaAnulacion(LocalDateTime.now(clock));
        pago.setMotivoAnulacion(motivo);
        Pago anulado = pagoRepository.save(pago);

        // El socio puede haber quedado ACTIVO apoyado en el pago que se acaba de anular.
        vencimientoService.recalcularEstadoDe(pago.getCliente().getId());

        return anulado;
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
        LocalDate fechaEfectiva = (fechaPago != null) ? fechaPago : LocalDate.now(clock);

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

        // 7. Calcular desde cuándo corre el período. Si el socio paga por adelantado, el
        // período nuevo arranca cuando termina el que ya tiene, no el día del cobro: si no,
        // pierde los días que le quedaban. Se encadena sea cual sea el plan. fechaPago no
        // cambia (es el día en que entró la plata), así que la caja y el dashboard tampoco.
        LocalDate inicioPeriodo = pagoRepository.findVencimientoVigenteAl(clienteId, fechaEfectiva)
                .filter(vencimientoVigente -> vencimientoVigente.isAfter(fechaEfectiva))
                .orElse(fechaEfectiva);

        // 8. Calcular la fecha de vencimiento sumando la duración en días del plan
        LocalDate fechaVencimiento = inicioPeriodo.plusDays(plan.getDuracion());

        // 9. Crear y guardar la entidad Pago
        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setPlan(plan);
        pago.setMontoAbonado(montoFinal);
        pago.setFechaPago(fechaEfectiva);
        pago.setFechaVencimiento(fechaVencimiento);
        pago.setRegistradoPor(registradoPor);

        Pago pagoGuardado = pagoRepository.save(pago);

        // 10. Regla de negocio: el pago activa al cliente, pero solo si efectivamente lo
        // deja al día. Un pago retroactivo cuyo período ya venció no reactiva a nadie:
        // el scheduler de vencimientos solo escala estados (nunca los revierte), así que
        // activar acá a ciegas dejaba al cliente ACTIVO indebidamente.
        if (fechaVencimiento.isAfter(LocalDate.now(clock))) {
            cliente.setEstado(EstadoCliente.ACTIVO);
            clienteRepository.save(cliente);
        }

        return pagoGuardado;
    }
}

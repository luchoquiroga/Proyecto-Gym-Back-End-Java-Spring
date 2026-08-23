package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.VencimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementación de la lógica de negocio para actualizar el estado de los clientes
 * según el vencimiento de su último pago.
 */
@Service
@RequiredArgsConstructor
public class VencimientoServiceImpl implements VencimientoService {

    private static final int DIAS_PARA_MOROSO = 1;
    private static final int DIAS_PARA_INACTIVO = 5;

    private final PagoRepository pagoRepository;
    private final ClienteRepository clienteRepository;

    @Override
    @Transactional
    public void actualizarEstadosPorVencimiento() {
        LocalDate hoy = LocalDate.now();
        List<Pago> ultimosPagos = pagoRepository.findUltimoPagoPorCadaCliente();

        for (Pago ultimoPago : ultimosPagos) {
            long diasVencido = ChronoUnit.DAYS.between(ultimoPago.getFechaVencimiento(), hoy);
            EstadoCliente estadoCalculado = calcularEstadoPorDiasVencido(diasVencido);

            if (estadoCalculado != null) {
                Cliente cliente = ultimoPago.getCliente();
                // Solo se escala el estado (ACTIVO -> MOROSO -> INACTIVO), nunca se revierte
                // automáticamente: evita reactivar clientes o pisar una baja manual.
                if (estadoCalculado.ordinal() > cliente.getEstado().ordinal()) {
                    cliente.setEstado(estadoCalculado);
                    clienteRepository.save(cliente);
                }
            }
        }
    }

    private EstadoCliente calcularEstadoPorDiasVencido(long diasVencido) {
        if (diasVencido >= DIAS_PARA_INACTIVO) {
            return EstadoCliente.INACTIVO;
        }
        if (diasVencido >= DIAS_PARA_MOROSO) {
            return EstadoCliente.MOROSO;
        }
        return null;
    }
}

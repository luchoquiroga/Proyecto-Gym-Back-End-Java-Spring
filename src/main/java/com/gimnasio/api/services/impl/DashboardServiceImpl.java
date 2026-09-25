package com.gimnasio.api.services.impl;

import com.gimnasio.api.dto.GananciasMensualesResponse;
import com.gimnasio.api.dto.SociosPorEstadoResponse;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de la lógica de negocio para los reportes del dashboard administrativo.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** Tope de meses de la serie: sin él, alguien puede pedir cincuenta años de consultas. */
    private static final int MAXIMO_MESES_SERIE = 24;

    /** Largo de la serie cuando no se indica {@code desde}: el último año, mes actual incluido. */
    private static final int MESES_SERIE_POR_DEFECTO = 12;

    private final PagoRepository pagoRepository;
    private final ClienteRepository clienteRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public GananciasMensualesResponse obtenerGananciasMensuales(Integer anio, Integer mes) {
        LocalDate hoy = LocalDate.now(clock);
        int anioEfectivo = (anio != null) ? anio : hoy.getYear();
        int mesEfectivo = (mes != null) ? mes : hoy.getMonthValue();

        // Se validan acá y no con YearMonth.of: ese tira DateTimeException, que no es un
        // IllegalArgumentException y terminaba en un 500. El tope de año es el mismo que
        // admite el formato AAAA-MM de /ganancias-por-mes.
        if (mesEfectivo < 1 || mesEfectivo > 12) {
            throw new IllegalArgumentException("El mes tiene que estar entre 1 y 12");
        }
        if (anioEfectivo < 1 || anioEfectivo > 9999) {
            throw new IllegalArgumentException("El año tiene que estar entre 1 y 9999");
        }
        return calcularGananciasDelMes(YearMonth.of(anioEfectivo, mesEfectivo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GananciasMensualesResponse> obtenerGananciasPorMes(YearMonth desde, YearMonth hasta) {
        YearMonth hastaEfectivo = (hasta != null) ? hasta : YearMonth.now(clock);
        YearMonth desdeEfectivo = (desde != null) ? desde : hastaEfectivo.minusMonths(MESES_SERIE_POR_DEFECTO - 1);

        if (desdeEfectivo.isAfter(hastaEfectivo)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }
        long cantidadMeses = desdeEfectivo.until(hastaEfectivo, ChronoUnit.MONTHS) + 1;
        if (cantidadMeses > MAXIMO_MESES_SERIE) {
            throw new IllegalArgumentException(
                    "El rango no puede superar los " + MAXIMO_MESES_SERIE + " meses");
        }

        // Una consulta por mes en vez de un GROUP BY: son a lo sumo 24, y así cada mes se
        // calcula exactamente igual que en /ganancias-mensuales. Además los meses sin cobros
        // salen en cero solos, sin tener que rellenar los huecos que dejaría un GROUP BY.
        List<GananciasMensualesResponse> serie = new ArrayList<>();
        for (YearMonth mes = desdeEfectivo; !mes.isAfter(hastaEfectivo); mes = mes.plusMonths(1)) {
            serie.add(calcularGananciasDelMes(mes));
        }
        return serie;
    }

    /**
     * La regla de qué cuenta como ingreso de un mes, en un solo lugar: pagos no anulados con
     * fecha de pago entre el primer y el último día del mes. La usan los dos endpoints, así
     * que un mes suma lo mismo en los dos.
     */
    private GananciasMensualesResponse calcularGananciasDelMes(YearMonth periodo) {
        // atEndOfMonth calcula el último día según el mes y el año (bisiestos incluidos).
        LocalDate inicio = periodo.atDay(1);
        LocalDate fin = periodo.atEndOfMonth();

        double totalGanancias = pagoRepository.sumarMontoAbonadoEntre(inicio, fin);
        long cantidadPagos = pagoRepository.countByFechaPagoBetweenAndAnuladoFalse(inicio, fin);

        return new GananciasMensualesResponse(
                periodo.getYear(), periodo.getMonthValue(), totalGanancias, cantidadPagos);
    }

    @Override
    @Transactional(readOnly = true)
    public SociosPorEstadoResponse contarSociosPorEstado() {
        return new SociosPorEstadoResponse(
                clienteRepository.countByEstado(EstadoCliente.ACTIVO),
                clienteRepository.countByEstado(EstadoCliente.MOROSO),
                clienteRepository.countByEstado(EstadoCliente.INACTIVO));
    }
}

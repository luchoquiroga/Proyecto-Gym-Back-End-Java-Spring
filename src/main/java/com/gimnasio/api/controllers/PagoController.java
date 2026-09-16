package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.AnulacionPagoRequest;
import com.gimnasio.api.dto.PagoRequest;
import com.gimnasio.api.dto.PagoResponse;
import com.gimnasio.api.dto.PaginaResponse;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.security.AuthPrincipal;
import com.gimnasio.api.services.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador REST versionado para la gestión de Pagos.
 */
@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    /**
     * Listado de pagos, opcionalmente acotado por fecha de cobro. Los filtros existen para
     * el desglose de ganancias del mes: el dashboard informa el total y esto devuelve las
     * filas que lo componen. Sin parámetros se comporta igual que antes.
     */
    @GetMapping
    public ResponseEntity<PaginaResponse<PagoResponse>> obtenerTodos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                PaginaResponse.desde(pagoService.obtenerTodos(desde, hasta, pageable), PagoResponse::desde));
    }

    /**
     * Detalle de un pago (solo ADMIN, ver SecurityConfig). Hasta la Fase 7 también lo podía
     * leer el CLIENTE dueño, con un chequeo de ownership acá adentro; se cerró porque el
     * portal del socio no muestra pagos, así que era una rama sin ningún consumidor.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PagoResponse> obtenerPorId(@PathVariable Integer id) {
        Pago pago = pagoService.obtenerPorId(id);
        return ResponseEntity.ok(PagoResponse.desde(pago));
    }

    /**
     * Pagos de un socio (solo ADMIN, ver SecurityConfig). Es la pantalla desde la que se
     * encuentra un pago mal cargado para anularlo. El id no lo escribe nadie: la web lo
     * tiene de la fila del listado en la que se hizo clic.
     */
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorCliente(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(pagoService.obtenerPagosPorCliente(clienteId).stream()
                .map(PagoResponse::desde)
                .toList());
    }

    /**
     * Anula un pago cargado por error (solo ADMIN, ver SecurityConfig). Es POST y no DELETE
     * a propósito: no se borra nada, se agrega un hecho. Tampoco existe un PUT para editar
     * un pago: corregir un importe es anular este y registrar el correcto.
     */
    @PostMapping("/{id}/anulacion")
    public ResponseEntity<PagoResponse> anular(@PathVariable Integer id,
                                               @Valid @RequestBody AnulacionPagoRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        // Quién anula sale del token, igual que el autor del cobro: si viniera del body,
        // el rastro de quién sacó plata del sistema no valdría nada.
        return ResponseEntity.ok(PagoResponse.desde(pagoService.anular(id, request.getMotivo(), principal.id())));
    }

    @PostMapping
    public ResponseEntity<PagoResponse> registrarPago(@Valid @RequestBody PagoRequest request,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        // El autor del cobro sale del token y no de PagoRequest: si viniera del body,
        // cualquiera podría registrar pagos a nombre de otro empleado y el rastro de
        // auditoría no valdría nada.
        Pago nuevoPago = pagoService.registrarPago(
                request.getClienteId(),
                request.getPlanId(),
                request.getMontoAbonado(),
                request.getFechaPago(),
                principal.id()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PagoResponse.desde(nuevoPago));
    }
}

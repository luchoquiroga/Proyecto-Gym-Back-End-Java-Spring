package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.PagoRequest;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.security.AuthPrincipal;
import com.gimnasio.api.services.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST versionado para la gestión de Pagos.
 */
@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @GetMapping
    public ResponseEntity<List<Pago>> obtenerTodos() {
        return ResponseEntity.ok(pagoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pago> obtenerPorId(@PathVariable Integer id,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        Pago pago = pagoService.obtenerPorId(id);
        // El id del principal solo significa "cliente" si el principal ES un cliente:
        // Usuario y Cliente son tablas distintas con secuencias de id independientes,
        // así que comparar ids sin mirar el rol dejaría pasar a un GERENCIA cuyo id de
        // usuario coincida por casualidad con el id del socio dueño del pago.
        boolean esAdmin = "ADMIN".equals(principal.rol());
        boolean esClienteDuenio = "CLIENTE".equals(principal.rol())
                && pago.getCliente().getId().equals(principal.id());
        if (!esAdmin && !esClienteDuenio) {
            throw new AccessDeniedException("No podés acceder a los pagos de otro cliente");
        }
        return ResponseEntity.ok(pago);
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Pago>> obtenerPagosPorCliente(@PathVariable Integer clienteId,
                                                              @AuthenticationPrincipal AuthPrincipal principal) {
        // Mismo motivo que en obtenerPorId: el id solo identifica a un socio si el
        // principal es un CLIENTE, nunca por el número suelto.
        boolean esAdmin = "ADMIN".equals(principal.rol());
        boolean esClienteDuenio = "CLIENTE".equals(principal.rol()) && clienteId.equals(principal.id());
        if (!esAdmin && !esClienteDuenio) {
            throw new AccessDeniedException("No podés acceder a los pagos de otro cliente");
        }
        return ResponseEntity.ok(pagoService.obtenerPagosPorCliente(clienteId));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Pago>> buscarPagosPorNombreCliente(@RequestParam String nombreCliente) {
        return ResponseEntity.ok(pagoService.buscarPagosPorNombreCliente(nombreCliente));
    }

    @PostMapping
    public ResponseEntity<Pago> registrarPago(@Valid @RequestBody PagoRequest request,
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
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoPago);
    }
}

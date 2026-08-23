package com.gimnasio.api.controllers;

import com.gimnasio.api.dto.PagoRequest;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.services.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Pago> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(pagoService.obtenerPorId(id));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Pago>> obtenerPagosPorCliente(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(pagoService.obtenerPagosPorCliente(clienteId));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Pago>> buscarPagosPorNombreCliente(@RequestParam String nombreCliente) {
        return ResponseEntity.ok(pagoService.buscarPagosPorNombreCliente(nombreCliente));
    }

    @PostMapping
    public ResponseEntity<Pago> registrarPago(@RequestBody PagoRequest request) {
        Pago nuevoPago = pagoService.registrarPago(
                request.getClienteId(),
                request.getPlanId(),
                request.getMontoAbonado(),
                request.getFechaPago()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoPago);
    }
}

package com.gimnasio.api.services.impl;

import com.gimnasio.api.dto.ClienteResponse;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementación de la lógica de negocio para la gestión de Clientes.
 */
@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    // Sin 0/O ni 1/I: se dicta en persona (o por teléfono) y esos pares se confunden fácil.
    private static final String ALFABETO_CODIGO_ACTIVACION = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LARGO_CODIGO_ACTIVACION = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteRepository clienteRepository;
    private final PagoRepository pagoRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<Cliente> obtenerTodos() {
        return clienteRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente obtenerPorId(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente buscarPorNombre(String nombre) {
        return clienteRepository.findByNombre(nombre)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con el nombre: " + nombre));
    }

    @Override
    @Transactional
    public Cliente crear(Cliente cliente) {
        // Por regla de negocio, un cliente recién registrado siempre inicia INACTIVO hasta que
        // abone un pago; no se respeta un "estado" que venga en el body del alta.
        cliente.setEstado(EstadoCliente.INACTIVO);

        // Un alta nunca debe poder pisar una fila existente vía un id enviado en el body.
        cliente.setId(null);

        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()
                && clienteRepository.findByEmail(cliente.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el email: " + cliente.getEmail());
        }

        if (cliente.getContrasena() != null && !cliente.getContrasena().isBlank()) {
            cliente.setContrasena(passwordEncoder.encode(cliente.getContrasena()));
        } else {
            // Sin credenciales todavía: el cliente las va a completar él mismo en
            // /registro, probando que es quien dice ser con este código de un solo uso.
            cliente.setCodigoActivacion(generarCodigoActivacionUnico());
        }

        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public Cliente actualizar(Integer id, Cliente clienteActualizado) {
        Cliente clienteExistente = obtenerPorId(id);

        clienteExistente.setNombre(clienteActualizado.getNombre());
        clienteExistente.setApellido(clienteActualizado.getApellido());
        clienteExistente.setTelefono(clienteActualizado.getTelefono());

        return clienteRepository.save(clienteExistente);
    }

    @Override
    @Transactional
    public Cliente cambiarEstado(Integer id, EstadoCliente nuevoEstado) {
        Cliente cliente = obtenerPorId(id);
        cliente.setEstado(nuevoEstado);
        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public void darDeBaja(Integer id) {
        // Soft delete (baja lógica): no borramos el registro de la BD para preservar historial de pagos
        Cliente cliente = obtenerPorId(id);
        cliente.setEstado(EstadoCliente.INACTIVO);
        clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public Cliente registrarCredenciales(String codigoActivacion, String email, String contrasena) {
        Cliente cliente = clienteRepository.findByCodigoActivacion(codigoActivacion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Código de activación inválido o ya utilizado. Pedí uno nuevo en el gimnasio."));

        if (cliente.getContrasena() != null) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada para este cliente. Iniciá sesión.");
        }

        if (clienteRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el email: " + email);
        }

        cliente.setEmail(email);
        cliente.setContrasena(passwordEncoder.encode(contrasena));
        // De un solo uso: una vez canjeado no debe volver a servir para reclamar la cuenta.
        cliente.setCodigoActivacion(null);
        return clienteRepository.save(cliente);
    }

    private String generarCodigoActivacionUnico() {
        String codigo;
        do {
            codigo = generarCodigoActivacion();
        } while (clienteRepository.existsByCodigoActivacion(codigo));
        return codigo;
    }

    private String generarCodigoActivacion() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO_ACTIVACION);
        for (int i = 0; i < LARGO_CODIGO_ACTIVACION; i++) {
            codigo.append(ALFABETO_CODIGO_ACTIVACION.charAt(RANDOM.nextInt(ALFABETO_CODIGO_ACTIVACION.length())));
        }
        return codigo.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean autenticar(String email, String contrasena) {
        return clienteRepository.findByEmail(email)
                .map(cliente -> cliente.getContrasena() != null
                        && passwordEncoder.matches(contrasena, cliente.getContrasena()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente buscarPorEmail(String email) {
        return clienteRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con el email: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> obtenerTodosConVencimiento() {
        List<Cliente> clientes = clienteRepository.findAll();

        // Una sola consulta trae el último pago de CADA cliente; de acá arriba resolvemos
        // todas las fechas de vencimiento con un Map en memoria en vez de consultar pago por
        // pago dentro del for de abajo (eso sería un N+1 contra la tabla de pagos).
        // La consulta devuelve TODOS los pagos empatados en la fecha máxima de cada cliente,
        // así que un socio con dos pagos que vencen el mismo día (por ejemplo dos pases
        // diarios comprados la misma fecha) aparece dos veces. Sin función de merge,
        // Collectors.toMap tira IllegalStateException por clave duplicada y el listado
        // entero devuelve 500; como la fecha empatada es la misma, quedarse con cualquiera
        // de las dos es correcto.
        Map<Integer, LocalDate> fechasVencimientoPorCliente = pagoRepository.findUltimoPagoPorCadaCliente().stream()
                .collect(Collectors.toMap(
                        pago -> pago.getCliente().getId(),
                        Pago::getFechaVencimiento,
                        (unaFecha, otraIgual) -> unaFecha));

        return clientes.stream()
                .map(cliente -> ClienteResponse.desde(cliente, fechasVencimientoPorCliente.get(cliente.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerRespuestaPorId(Integer id) {
        Cliente cliente = obtenerPorId(id);
        return ClienteResponse.desde(cliente, fechaVencimientoVigente(cliente.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse buscarPorNombreConVencimiento(String nombre) {
        Cliente cliente = buscarPorNombre(nombre);
        return ClienteResponse.desde(cliente, fechaVencimientoVigente(cliente.getId()));
    }

    // Fecha de vencimiento vigente de UN solo cliente: acá no hace falta traer la tabla
    // entera con findUltimoPagoPorCadaCliente(), alcanza con la última fila de ese socio.
    private LocalDate fechaVencimientoVigente(Integer clienteId) {
        return pagoRepository.findTopByClienteIdOrderByFechaVencimientoDesc(clienteId)
                .map(Pago::getFechaVencimiento)
                .orElse(null);
    }
}

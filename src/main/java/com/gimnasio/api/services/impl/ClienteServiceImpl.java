package com.gimnasio.api.services.impl;

import com.gimnasio.api.dto.ClienteRequest;
import com.gimnasio.api.dto.ClienteResponse;
import com.gimnasio.api.dto.PaginaResponse;
import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.services.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado con id: " + id));
    }


    @Override
    @Transactional
    public Cliente crear(ClienteRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && clienteRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el email: " + request.getEmail());
        }

        Cliente cliente = new Cliente();
        cliente.setNombre(request.getNombre());
        cliente.setApellido(request.getApellido());
        cliente.setTelefono(request.getTelefono());
        cliente.setEmail(request.getEmail());

        // Por regla de negocio, un cliente recién registrado siempre inicia INACTIVO hasta
        // que abone un pago. A diferencia de la versión vieja (que recibía la entidad
        // Cliente completa y tenía que anular "estado" e "id" a mano), ClienteRequest ni
        // siquiera tiene esos campos: no hay nada que pisar.
        cliente.setEstado(EstadoCliente.INACTIVO);

        if (request.getContrasena() != null && !request.getContrasena().isBlank()) {
            cliente.setContrasena(passwordEncoder.encode(request.getContrasena()));
        } else {
            // Sin credenciales todavía: el cliente las va a completar él mismo en
            // /registro, probando que es quien dice ser con este código de un solo uso.
            cliente.setCodigoActivacion(generarCodigoActivacionUnico());
        }

        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public ClienteResponse actualizar(Integer id, ClienteRequest request) {
        Cliente clienteExistente = obtenerPorId(id);

        // Solo datos de contacto. El email NO se edita acá a propósito: es la identidad
        // de login del socio en el portal, y cambiárselo desde el mostrador le sacaría el
        // acceso a su cuenta sin que se entere. La contraseña tampoco: la define el propio
        // socio en /registro. Ambos campos existen en ClienteRequest porque el alta sí los
        // usa, y acá se ignoran deliberadamente (ver el javadoc de ClienteRequest).
        clienteExistente.setNombre(request.getNombre());
        clienteExistente.setApellido(request.getApellido());
        clienteExistente.setTelefono(request.getTelefono());

        Cliente guardado = clienteRepository.save(clienteExistente);
        return ClienteResponse.desde(guardado, ultimoPago(guardado.getId()));
    }

    @Override
    @Transactional
    public ClienteResponse cambiarEstado(Integer id, EstadoCliente nuevoEstado) {
        // Un socio se activa pagando, nunca con un cambio de estado a mano. Sin esto, este
        // endpoint es la puerta de atrás de las dos reglas de dinero de la Fase 1: que un
        // pago menor al precio del plan se rechaza, y que la activación solo ocurre si el
        // vencimiento calculado es futuro. Las dos se esquivaban con un clic.
        // Si alguna vez hace falta un socio de cortesía, eso es un pago de importe cero
        // contra un plan de cortesía -- que queda registrado y auditado -- y no un estado
        // que aparece sin que nadie sepa quién lo puso.
        if (nuevoEstado == EstadoCliente.ACTIVO) {
            throw new IllegalArgumentException(
                    "Un socio no se activa a mano: se activa registrándole un pago válido.");
        }

        Cliente cliente = obtenerPorId(id);
        cliente.setEstado(nuevoEstado);
        Cliente guardado = clienteRepository.save(cliente);
        return ClienteResponse.desde(guardado, ultimoPago(guardado.getId()));
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
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado con el email: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> obtenerTodosConVencimiento(Pageable pageable) {
        Page<Cliente> paginaClientes = clienteRepository.findAll(pageable);

        // Una sola consulta trae el último pago de CADA cliente (no solo los de esta
        // página: PagoRepository no tiene un método acotado a un subconjunto de ids), y de
        // acá arriba resolvemos todas las fechas de vencimiento con un Map en memoria en
        // vez de consultar pago por pago dentro del map de abajo (eso sería un N+1 contra
        // la tabla de pagos). Sigue siendo UNA sola consulta a pagos sin importar el
        // tamaño de página pedido.
        // La consulta devuelve TODOS los pagos empatados en la fecha máxima de cada cliente,
        // así que un socio con dos pagos que vencen el mismo día (por ejemplo dos pases
        // diarios comprados la misma fecha) aparece dos veces. Sin función de merge,
        // Collectors.toMap tira IllegalStateException por clave duplicada; como la fecha
        // empatada es la misma, quedarse con cualquiera de las dos es correcto.
        Map<Integer, Pago> ultimoPagoPorCliente = pagoRepository.findUltimoPagoPorCadaCliente().stream()
                .collect(Collectors.toMap(
                        pago -> pago.getCliente().getId(),
                        pago -> pago,
                        (unPago, otroEmpatado) -> unPago));

        return PaginaResponse.desde(paginaClientes,
                cliente -> ClienteResponse.desde(cliente, ultimoPagoPorCliente.get(cliente.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerRespuestaPorId(Integer id) {
        Cliente cliente = obtenerPorId(id);
        return ClienteResponse.desde(cliente, ultimoPago(cliente.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> buscarPorNombreConVencimiento(String nombre) {
        return clienteRepository.findByNombreContainingIgnoreCase(nombre).stream()
                .map(cliente -> ClienteResponse.desde(cliente, ultimoPago(cliente.getId())))
                .toList();
    }

    // Último pago de UN solo cliente, del que salen su fecha de vencimiento y su plan
    // vigente: acá no hace falta traer la tabla entera con findUltimoPagoPorCadaCliente(),
    // alcanza con la última fila de ese socio.
    private Pago ultimoPago(Integer clienteId) {
        return pagoRepository.findTopByClienteIdAndAnuladoFalseOrderByFechaVencimientoDesc(clienteId)
                .orElse(null);
    }
}

package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.services.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación de la lógica de negocio para la gestión de Clientes.
 */
@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
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
    public Cliente registrarCredenciales(String nombre, String apellido, String telefono,
                                          String email, String contrasena) {
        Cliente cliente = clienteRepository
                .findByNombreIgnoreCaseAndApellidoIgnoreCaseAndTelefono(nombre, apellido, telefono)
                .orElseThrow(() -> new RuntimeException(
                        "Cliente no encontrado con esos datos. Acercate al gimnasio para verificar tu registro."));

        if (cliente.getContrasena() != null) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada para este cliente. Iniciá sesión.");
        }

        if (clienteRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el email: " + email);
        }

        cliente.setEmail(email);
        cliente.setContrasena(passwordEncoder.encode(contrasena));
        return clienteRepository.save(cliente);
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
}

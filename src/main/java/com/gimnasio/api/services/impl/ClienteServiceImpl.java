package com.gimnasio.api.services.impl;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.services.ClienteService;
import lombok.RequiredArgsConstructor;
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
        // Por regla de negocio, un cliente recién registrado inicia INACTIVO hasta que abone un pago
        if (cliente.getEstado() == null) {
            cliente.setEstado(EstadoCliente.INACTIVO);
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
}

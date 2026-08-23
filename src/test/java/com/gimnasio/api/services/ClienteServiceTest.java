package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.services.impl.ClienteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteServiceImpl clienteService;

    private Cliente clientePrueba;

    @BeforeEach
    void setUp() {
        clientePrueba = new Cliente(1, "Carlos", "Gómez", "123456789", EstadoCliente.INACTIVO);
    }

    @Test
    @DisplayName("Debe crear un cliente con estado inicial INACTIVO")
    void crear_deberiaGuardarClienteConEstadoInactivo() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente nuevo = new Cliente(null, "Carlos", "Gómez", "123456789", null);
        Cliente resultado = clienteService.crear(nuevo);

        assertNotNull(resultado);
        assertEquals(EstadoCliente.INACTIVO, resultado.getEstado());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Debe retornar cliente cuando el ID existe")
    void obtenerPorId_cuandoExiste_deberiaRetornarCliente() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));

        Cliente resultado = clienteService.obtenerPorId(1);

        assertNotNull(resultado);
        assertEquals("Carlos", resultado.getNombre());
        verify(clienteRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el ID no existe")
    void obtenerPorId_cuandoNoExiste_deberiaLanzarExcepcion() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            clienteService.obtenerPorId(99);
        });

        assertTrue(exception.getMessage().contains("no encontrado con id: 99"));
        verify(clienteRepository, times(1)).findById(99);
    }

    @Test
    @DisplayName("darDeBaja debe aplicar Soft Delete pasando el estado a INACTIVO")
    void darDeBaja_deberiaCambiarEstadoAInactivo() {
        clientePrueba.setEstado(EstadoCliente.ACTIVO);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(clientePrueba));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clienteService.darDeBaja(1);

        assertEquals(EstadoCliente.INACTIVO, clientePrueba.getEstado());
        verify(clienteRepository, times(1)).save(clientePrueba);
    }
}

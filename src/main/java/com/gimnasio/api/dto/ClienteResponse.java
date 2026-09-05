package com.gimnasio.api.dto;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos públicos de un cliente, seguros para exponer en respuestas de autenticación
 * (nunca incluye la contraseña, aunque esté hasheada).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteResponse {

    private Integer id;
    private String nombre;
    private String apellido;
    private String email;
    private EstadoCliente estado;

    public static ClienteResponse desde(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getEmail(),
                cliente.getEstado()
        );
    }
}

package com.gimnasio.api.dto;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del alta de un cliente (POST /api/v1/clientes). A diferencia de
 * devolver la entidad Cliente directamente, expone el código de activación una
 * única vez acá (la entidad lo tiene @JsonIgnore en cualquier otra respuesta) para
 * que el staff se lo entregue al cliente en persona; es null si el cliente se
 * cargó ya con email y contraseña directamente, sin necesitar /registro después.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteAltaResponse {

    private Integer id;
    private String nombre;
    private String apellido;
    private String telefono;
    private String documento;
    private EstadoCliente estado;
    private String codigoActivacion;

    public static ClienteAltaResponse desde(Cliente cliente) {
        return new ClienteAltaResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getTelefono(),
                cliente.getDocumento(),
                cliente.getEstado(),
                cliente.getCodigoActivacion()
        );
    }
}

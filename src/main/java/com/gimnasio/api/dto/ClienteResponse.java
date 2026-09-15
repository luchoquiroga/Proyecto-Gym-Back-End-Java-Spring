package com.gimnasio.api.dto;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.enums.EstadoCliente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
    private String telefono;
    private String email;
    private EstadoCliente estado;

    /**
     * Fecha hasta la que el socio tiene la cuota paga (vencimiento de su último pago
     * registrado). Es la respuesta a "¿está al día?" sin exponer ningún monto: GERENCIA
     * cobra y necesita saber esto en el mostrador, pero tiene vedada la lectura de pagos
     * (esa sí trae plata). Se calcula al vuelo a partir de la tabla de pagos, nunca se
     * persiste en Cliente. Null si el socio todavía no registró ningún pago.
     */
    private LocalDate fechaVencimiento;

    /**
     * Variante sin fecha de vencimiento, para los llamadores que no la necesitan
     * (respuestas de login/refresh de cliente, que no pasan por la lógica de pagos).
     */
    public static ClienteResponse desde(Cliente cliente) {
        return desde(cliente, null);
    }

    public static ClienteResponse desde(Cliente cliente, LocalDate fechaVencimiento) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getTelefono(),
                cliente.getEmail(),
                cliente.getEstado(),
                fechaVencimiento
        );
    }
}

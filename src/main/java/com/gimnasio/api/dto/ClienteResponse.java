package com.gimnasio.api.dto;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
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
     * Plan que el socio tiene vigente, es decir el de su último pago. `Cliente` no
     * referencia a `Plan`: el plan actual es un dato derivado de la tabla de pagos, igual
     * que la fecha de vencimiento, y por el mismo motivo no se persiste. Solo nombre e id,
     * nunca el precio: esto lo lee el propio socio en su portal y GERENCIA en el
     * mostrador, y ninguno de los dos tiene por qué recibir un monto por esta vía.
     * Null si el socio todavía no registró ningún pago.
     */
    private PlanVigente planVigente;

    public record PlanVigente(Integer id, String nombre) {
    }

    /**
     * Variante sin datos derivados de pagos, para los llamadores que no los necesitan
     * (respuestas de login/refresh de cliente, que no pasan por la lógica de pagos).
     */
    public static ClienteResponse desde(Cliente cliente) {
        return desde(cliente, (Pago) null);
    }

    /**
     * Arma la respuesta a partir del socio y de su último pago, del que salen tanto la
     * fecha de vencimiento como el plan vigente. Recibe el pago entero y no solo la fecha
     * justamente para eso: son dos datos que vienen de la misma fila, y pedirlos por
     * separado era una consulta de más.
     *
     * @param ultimoPago el último pago del socio, o null si nunca pagó.
     */
    public static ClienteResponse desde(Cliente cliente, Pago ultimoPago) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getTelefono(),
                cliente.getEmail(),
                cliente.getEstado(),
                ultimoPago == null ? null : ultimoPago.getFechaVencimiento(),
                ultimoPago == null ? null
                        : new PlanVigente(ultimoPago.getPlan().getId(), ultimoPago.getPlan().getNombre())
        );
    }
}

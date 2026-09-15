package com.gimnasio.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para alta (POST) y edición (PUT) de un Cliente por parte del staff.
 *
 * A propósito NO tiene id, estado ni codigoActivacion: son campos que el llamador
 * no puede auto-asignarse. "estado" decide si el socio está activo (si pagó), y
 * "codigoActivacion" es la prueba de identidad de un solo uso para reclamar la
 * cuenta (ver Cliente.codigoActivacion). Que estos campos directamente no existan
 * en este DTO cierra el mass assignment de raíz, en vez de depender de anularlos
 * a mano después en el service (como se hacía antes con setId(null) etc.).
 *
 * En PUT (edición) solo se aplican nombre, apellido y telefono: email y contrasena se
 * ignoran deliberadamente, porque el email es la identidad de login del socio y la
 * contraseña la define él mismo en /registro. Se editan por sus propios flujos, no
 * desde el mostrador.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;

    private String telefono;

    @Email(message = "El email no tiene un formato válido")
    private String email;

    /**
     * Opcional: el staff puede dar de alta a un socio sin credenciales todavía (el
     * flujo normal de mostrador), y el cliente las completa después por su cuenta en
     * /registro usando el código de activación que se le entregó en persona.
     */
    private String contrasena;
}

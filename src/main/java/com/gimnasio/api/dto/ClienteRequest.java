package com.gimnasio.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String apellido;

    @Size(max = 50, message = "El teléfono no puede superar los 50 caracteres")
    private String telefono;

    /**
     * Documento de identidad. Obligatorio: es el único dato que distingue a dos socios
     * homónimos, y ese es el motivo por el que existe el campo.
     *
     * No se valida contra el formato del DNI argentino a propósito: un socio extranjero con
     * pasaporte o cédula tiene que poder anotarse, y una validación demasiado estricta
     * termina esquivándose escribiendo cualquier cosa, que es peor que no validar. Lo que sí
     * se hace es normalizarlo antes de guardarlo, así el mismo documento escrito con o sin
     * puntos no entra dos veces.
     */
    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento no puede superar los 20 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9.\\- ]+$",
            message = "El documento solo puede tener letras, números, puntos, espacios y guiones")
    private String documento;

    @Email(message = "El email no tiene un formato válido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    /**
     * Opcional: el staff puede dar de alta a un socio sin credenciales todavía (el
     * flujo normal de mostrador), y el cliente las completa después por su cuenta en
     * /registro usando el código de activación que se le entregó en persona.
     */
    // 72 porque BCrypt solo usa los primeros 72 bytes: más largo, el encoder la rechaza con
    // un error en inglés, y antes ni eso (dos claves con el mismo comienzo eran iguales).
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String contrasena;
}

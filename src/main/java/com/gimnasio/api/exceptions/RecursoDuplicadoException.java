package com.gimnasio.api.exceptions;

/**
 * Un dato que tiene que ser único ya pertenece a otra fila: el documento de un socio, por
 * ejemplo.
 *
 * <p>Existe para poder devolver <b>409</b> con un mensaje que diga qué pasó. Las dos
 * alternativas que había eran peores: {@code IllegalArgumentException} da 400, y el dato no
 * está mal formado —el conflicto es con el estado de la base, no con lo que se escribió—; y
 * dejar que reviente la restricción de la base da un 409 genérico ("Ya existe un registro
 * con esos datos"), porque el mensaje de Postgres no se puede exponer: incluye el nombre de
 * la constraint y el valor de la clave, que es detalle interno del esquema.
 *
 * <p>El mensaje de esta excepción sí se expone tal cual, así que se redacta para el usuario
 * final y nunca lleva detalle de implementación.
 */
public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}

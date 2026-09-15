package com.gimnasio.api.exceptions;

/**
 * Señala que un recurso solicitado no existe (por ejemplo, un id que no está en la BD).
 * <p>
 * Existe para que el 404 salga del TIPO de la excepción, no de leer su texto: antes,
 * {@code GlobalExceptionHandler} decidía el código HTTP buscando la frase "no encontrado"
 * dentro del mensaje de cualquier {@link RuntimeException}, lo que hacía que un
 * {@link NullPointerException} inesperado (que también es una RuntimeException) terminara
 * devolviendo 400 con su mensaje interno en el cuerpo, en vez de un 500 genérico. Al lanzar
 * este tipo específico para "no encontrado", el handler puede distinguir ese caso sin
 * inspeccionar el texto del mensaje.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

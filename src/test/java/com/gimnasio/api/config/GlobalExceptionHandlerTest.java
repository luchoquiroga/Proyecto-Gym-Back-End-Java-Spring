package com.gimnasio.api.config;

import com.gimnasio.api.dto.ErrorResponse;
import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DataIntegrityViolationException debe mapear a 409 con mensaje genérico (nunca el detalle interno de la constraint)")
    void manejarViolacionDeIntegridad_deberiaDevolver409ConMensajeGenerico() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_clientes_email\" Detail: Key (email)=(a@a.com) already exists.");

        ResponseEntity<ErrorResponse> respuesta = handler.manejarViolacionDeIntegridad(ex);

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertFalse(respuesta.getBody().getMensaje().contains("constraint"));
        assertFalse(respuesta.getBody().getMensaje().contains("email"));
    }

    @Test
    @DisplayName("AccessDeniedException debe mapear a 403")
    void manejarAccesoDenegado_deberiaDevolver403() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarAccesoDenegado(new AccessDeniedException("no"));

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
    }

    @Test
    @DisplayName("RecursoNoEncontradoException debe mapear a 404 con su propio mensaje")
    void manejarRecursoNoEncontrado_deberiaDevolver404ConMensajePropio() {
        RecursoNoEncontradoException ex = new RecursoNoEncontradoException("Cliente no encontrado con id: 42");

        ResponseEntity<ErrorResponse> respuesta = handler.manejarRecursoNoEncontrado(ex);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("Cliente no encontrado con id: 42", respuesta.getBody().getMensaje());
    }

    @Test
    @DisplayName("Una RuntimeException inesperada (ej. NullPointerException) debe mapear a 500 sin filtrar el detalle interno")
    void manejarErrorInesperado_conRuntimeExceptionInesperada_deberiaDevolver500SinFiltrarDetalleInterno() {
        // "detalle interno de la base de datos" simula información que jamás debería llegar al cliente.
        NullPointerException ex = new NullPointerException("detalle interno de la base de datos");

        ResponseEntity<ErrorResponse> respuesta = handler.manejarErrorInesperado(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertFalse(respuesta.getBody().getMensaje().contains("detalle interno de la base de datos"));
    }
}

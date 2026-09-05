package com.gimnasio.api.config;

import com.gimnasio.api.dto.ErrorResponse;
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
}

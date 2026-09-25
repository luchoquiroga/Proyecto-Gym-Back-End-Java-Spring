package com.gimnasio.api.config;

import com.gimnasio.api.dto.ErrorResponse;
import com.gimnasio.api.exceptions.RecursoDuplicadoException;
import com.gimnasio.api.exceptions.RecursoNoEncontradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor global de excepciones para toda la API REST.
 * Convierte excepciones de Java en respuestas JSON estructuradas y con códigos HTTP correctos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarArgumentoInvalido(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        // El mensaje es nuestro, redactado para el usuario final: exponerlo está bien.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoDuplicado(RecursoDuplicadoException ex) {
        // Mensaje nuestro, redactado para el usuario final: decirle cuál es el dato repetido
        // es justamente lo que le permite resolverlo. El 409 genérico de más abajo existe
        // para cuando la que salta es la restricción de la base, cuyo mensaje sí es interno.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> manejarOrdenInvalido(PropertyReferenceException ex) {
        // La lanza Spring Data cuando el ?sort= de un listado paginado nombra un campo que la
        // entidad no tiene. Es un error de quien llama, no del servidor: sin este handler caía
        // en el 500 genérico. Se nombra el campo porque es el que mandó el propio llamador,
        // no un detalle interno (ex.getMessage() sí lo sería: lista las propiedades cercanas).
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "No se puede ordenar por '" + ex.getPropertyName() + "'",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoDeParametroInvalido(MethodArgumentTypeMismatchException ex) {
        // La lanza Spring cuando un parámetro de la URL no se puede convertir al tipo del método:
        // ?anio=abc para un Integer, o /clientes/abc para un id numérico. Igual que el sort
        // inexistente, es un error de quien llama. Se nombra el parámetro pero no se repite el
        // valor recibido: no hace falta para corregirlo y así no se refleja input arbitrario.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "El parámetro '" + ex.getName() + "' tiene un valor inválido",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        // JSON mal formado, un enum que no existe ("rol": "SUPERADMIN") o una fecha imposible
        // ("2026-02-30"). Es un error de quien llama; sin este handler caía en el 500 genérico.
        // No se repite ex.getMessage(): trae detalles del parser y de las clases internas.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "El cuerpo de la solicitud es inválido o está mal formado",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(MissingServletRequestParameterException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "No tenés permisos para acceder a este recurso",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarViolacionDeIntegridad(DataIntegrityViolationException ex) {
        // Nunca se expone ex.getMessage() acá: en Postgres incluye el nombre de la constraint
        // y el detalle de la clave, información interna del esquema que no debe llegar al cliente.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Ya existe un registro con esos datos",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacionInvalida(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("status", HttpStatus.BAD_REQUEST.value());
        cuerpo.put("mensaje", "Datos inválidos");
        cuerpo.put("errores", errores);
        cuerpo.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex) {
        // Las excepciones propias de Spring MVC (ruta inexistente, método no admitido,
        // Content-Type no soportado...) ya traen su código 4xx. Como este handler atrapa
        // Exception, sin esta rama todas se volvían un 500 y ensuciaban el log de errores.
        if (ex instanceof org.springframework.web.ErrorResponse errorDeSpring
                && errorDeSpring.getStatusCode().is4xxClientError()) {
            HttpStatusCode codigo = errorDeSpring.getStatusCode();
            ErrorResponse error = new ErrorResponse(codigo.value(), mensajePara(codigo), LocalDateTime.now());
            return ResponseEntity.status(codigo).body(error);
        }

        // El detalle completo queda solo en el log del servidor; al cliente nunca le llega
        // el mensaje interno de la excepción (podría filtrar detalles de implementación).
        log.error("Error inesperado en el servidor", ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocurrió un error inesperado en el servidor",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private String mensajePara(HttpStatusCode codigo) {
        return switch (codigo.value()) {
            case 404 -> "El recurso solicitado no existe";
            case 405 -> "Método HTTP no permitido para este recurso";
            case 415 -> "Tipo de contenido no soportado: se espera JSON";
            default -> "La solicitud es inválida";
        };
    }
}

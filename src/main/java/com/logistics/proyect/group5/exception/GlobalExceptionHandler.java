package com.logistics.proyect.group5.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return validationErrorResponse(ex.getBindingResult());
    }

    /** Validación de {@code @ModelAttribute} (p. ej. multipart en productos). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindExceptions(BindException ex) {
        return validationErrorResponse(ex.getBindingResult());
    }

    /** Validación de parámetros anotados directamente con Jakarta Validation. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolations(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(field, violation.getMessage());
        });
        return errorResponse(HttpStatus.BAD_REQUEST, "Error de Validación", null, errors);
    }

    private ResponseEntity<Map<String, Object>> validationErrorResponse(BindingResult bindingResult) {
        Map<String, String> errors = new HashMap<>();
        bindingResult.getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        return errorResponse(HttpStatus.BAD_REQUEST, "Error de Validación", null, errors);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, "Recurso No Encontrado", ex.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return errorResponse(
                HttpStatus.FORBIDDEN,
                "Acceso Denegado",
                "No tienes permisos para realizar esta acción",
                null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "Autenticación Fallida",
                "Correo electrónico o contraseña incorrectos",
                null);
    }

    /** JSON malformado, cuerpo ausente o tipos incompatibles en el body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Petición Inválida",
                "El cuerpo de la solicitud no es válido. Revisa el formato JSON y los tipos de datos.",
                null);
    }

    /** UUID o número con formato inválido en la ruta o en un query param. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "El valor del parámetro '" + ex.getName() + "' no es válido.";
        return errorResponse(HttpStatus.BAD_REQUEST, "Petición Inválida", message, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "Falta el parámetro obligatorio '" + ex.getParameterName() + "'.";
        return errorResponse(HttpStatus.BAD_REQUEST, "Petición Inválida", message, null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        String message = "Falta la parte obligatoria '" + ex.getRequestPartName() + "' en la solicitud.";
        return errorResponse(HttpStatus.BAD_REQUEST, "Petición Inválida", message, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return errorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de Contenido No Soportado",
                "El tipo de contenido de la solicitud no está soportado.",
                null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return errorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Archivo Demasiado Grande",
                "El archivo supera el tamaño máximo permitido (10 MB).",
                null);
    }

    /**
     * Conflictos de integridad: duplicados, límites de columna o FKs.
     * No se devuelve el detalle SQL para no filtrar nombres de tablas ni columnas.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Conflicto de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return errorResponse(
                HttpStatus.CONFLICT,
                "Conflicto de Datos",
                "No se pudo completar la operación: los datos entran en conflicto con un registro existente o superan un límite permitido.",
                null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, "Recurso No Encontrado", "El recurso solicitado no existe.", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Petición Inválida", ex.getMessage(), null);
    }

    /**
     * Red de seguridad. El detalle se registra en el servidor, pero el cliente
     * recibe un mensaje genérico para no exponer SQL, rutas ni clases internas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Error no controlado", ex);
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error Interno del Servidor",
                "Ocurrió un error interno. Inténtalo de nuevo más tarde.",
                null);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String error,
            String message,
            Map<String, String> details) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("error", error);
        if (message != null) {
            response.put("message", message);
        }
        if (details != null && !details.isEmpty()) {
            response.put("details", details);
        }
        return ResponseEntity.status(status).body(response);
    }
}

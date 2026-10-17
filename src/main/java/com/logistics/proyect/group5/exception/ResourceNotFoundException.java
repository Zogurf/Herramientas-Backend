package com.logistics.proyect.group5.exception;

/**
 * Recurso inexistente. Se traduce a HTTP 404 en {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

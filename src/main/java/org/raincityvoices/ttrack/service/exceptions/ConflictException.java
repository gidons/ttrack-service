package org.raincityvoices.ttrack.service.exceptions;

/**
 * Exception thrown when a request conflicts with existing state.
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {
    
    public ConflictException(String message) {
        super(message);
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

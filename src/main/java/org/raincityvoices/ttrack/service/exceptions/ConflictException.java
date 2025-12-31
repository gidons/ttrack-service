package org.raincityvoices.ttrack.service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a request conflicts with existing state.
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(value=HttpStatus.CONFLICT, reason="Resource already exists or is out of date.")
public class ConflictException extends RuntimeException {
    
    public ConflictException(String message) {
        super(message);
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

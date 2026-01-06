package org.raincityvoices.ttrack.service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a request conflicts with existing state.
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(value=HttpStatus.UNAUTHORIZED, reason="Unauthorized access to resource.")
public class UnauthorizedException extends RuntimeException {
    
    public UnauthorizedException(String message) {
        super(message);
    }
    
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}

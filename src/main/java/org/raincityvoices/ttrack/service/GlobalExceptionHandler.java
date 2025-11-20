package org.raincityvoices.ttrack.service;

import org.raincityvoices.ttrack.service.exceptions.BadRequestException;
import org.raincityvoices.ttrack.service.exceptions.ConflictException;
import org.raincityvoices.ttrack.service.exceptions.NotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(NotFoundException.class)
    public String handleNotFoundException(NotFoundException ex) {
        log.warn("NotFoundException: {}", ex.getMessage());
        return "/error/404";
    }
    
    @ExceptionHandler(BadRequestException.class)
    public String handleBadRequestException(BadRequestException ex) {
        log.warn("BadRequestException: {}", ex.getMessage());
        return "/error/400";
    }
    
    @ExceptionHandler(ConflictException.class)
    public String handleConflictException(ConflictException ex) {
        log.warn("ConflictException: {}", ex.getMessage());
        return "/error/409";
    }
}

package com.skilltracker.exception;

import org.springframework.http.HttpStatus;

public class PermissionDeniedException extends DomainException {

    public PermissionDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}

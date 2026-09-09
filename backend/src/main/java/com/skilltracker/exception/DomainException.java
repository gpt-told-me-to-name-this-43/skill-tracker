package com.skilltracker.exception;

import org.springframework.http.HttpStatus;

/** Base class for business-rule failures that map onto a specific HTTP status. */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

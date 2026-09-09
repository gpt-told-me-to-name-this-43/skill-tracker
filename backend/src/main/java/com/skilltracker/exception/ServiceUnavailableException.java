package com.skilltracker.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

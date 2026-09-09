package com.skilltracker.exception;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends DomainException {

    public UnprocessableEntityException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}

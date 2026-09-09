package com.skilltracker.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error envelope the React client parses: {@code {"error": {"message": ..., "details": ...}}}.
 */
public record ErrorResponse(
        @JsonInclude(JsonInclude.Include.ALWAYS) ErrorBody error) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(new ErrorBody(message, null));
    }

    public static ErrorResponse of(String message, Object details) {
        return new ErrorResponse(new ErrorBody(message, details));
    }

    public record ErrorBody(
            String message,
            @JsonInclude(JsonInclude.Include.ALWAYS) Object details) {}
}

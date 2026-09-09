package com.skilltracker.exception;

import com.skilltracker.json.UtcLocalDateTimeDeserializer;
import jakarta.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Renders every failure through the single error envelope the previous FastAPI backend used, so the
 * React client keeps reading {@code error.message} unchanged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Validation error";

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
        if (exception.status() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ObjectError error : exception.getBindingResult().getAllErrors()) {
            details.add(toDetail(error));
        }
        return validationResponse(details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException exception) {
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            for (var error : result.getResolvableErrors()) {
                String code = error.getCodes() == null || error.getCodes().length == 0
                        ? "value_error"
                        : error.getCodes()[error.getCodes().length - 1];
                details.add(new ValidationErrorDetail(
                        code, List.of("query", name == null ? "" : name), error.getDefaultMessage(), null));
            }
        }
        return validationResponse(details);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException exception) {
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            details.add(new ValidationErrorDetail(
                    "value_error",
                    List.of("query", String.valueOf(violation.getPropertyPath())),
                    violation.getMessage(),
                    violation.getInvalidValue()));
        }
        return validationResponse(details);
    }

    /**
     * A body that cannot be parsed, or that arrives with a content type the endpoint does not read,
     * was a 422 under FastAPI rather than a 400/415.
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleUnreadableBody(Exception exception) {
        return validationResponse(List.of(bodyDetail(exception)));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(TypeMismatchException exception) {
        String name = exception
                        instanceof
                        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : exception.getPropertyName();
        return validationResponse(List.of(new ValidationErrorDetail(
                "type_error",
                List.of("query", name == null ? "" : name),
                "Input should be a valid value",
                exception.getValue())));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("Invalid multipart file payload"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("Not Found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.of("Method Not Allowed"));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponse(ErrorResponseException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(ErrorResponse.of(status.getReasonPhrase()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception while serving a request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of("Internal Server Error"));
    }

    private ValidationErrorDetail bodyDetail(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof UtcLocalDateTimeDeserializer.InvalidTimestampException invalidTimestamp) {
            return new ValidationErrorDetail(
                    "datetime_parsing", List.of("body"), invalidTimestamp.getMessage(), invalidTimestamp.value());
        }
        if (cause instanceof MismatchedInputException mismatched) {
            List<Object> location = new ArrayList<>();
            location.add("body");
            mismatched.getPath().forEach(reference -> {
                if (reference.getPropertyName() != null) {
                    location.add(reference.getPropertyName());
                }
            });
            return new ValidationErrorDetail("value_error", location, mismatched.getOriginalMessage(), null);
        }
        return new ValidationErrorDetail("value_error", List.of("body"), "Input should be a valid dictionary", null);
    }

    private ValidationErrorDetail toDetail(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        Object rejected = error instanceof FieldError fieldError ? fieldError.getRejectedValue() : null;
        String code = error.getCode() == null ? "value_error" : error.getCode();
        return new ValidationErrorDetail(code, List.of("body", field), error.getDefaultMessage(), rejected);
    }

    private ResponseEntity<ErrorResponse> validationResponse(List<ValidationErrorDetail> details) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(VALIDATION_MESSAGE, details));
    }
}

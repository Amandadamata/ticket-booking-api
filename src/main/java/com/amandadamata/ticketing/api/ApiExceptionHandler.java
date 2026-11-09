package com.amandadamata.ticketing.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.amandadamata.ticketing.booking.BookingException;

/**
 * Every error is an RFC 9457 problem detail ({@code application/problem+json}) with a stable
 * {@code code}, so clients don't have to parse messages.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_BASE = "https://github.com/Amandadamata/ticket-booking-api#";

    @ExceptionHandler(BookingException.class)
    ProblemDetail handleBooking(BookingException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(exception.title());
        problem.setType(URI.create(TYPE_BASE + exception.code().toLowerCase().replace('_', '-')));
        problem.setProperty("code", exception.code());
        exception.details().forEach(problem::setProperty);
        return problem;
    }

    /** Invalid request body: lists every field that failed, not just the first one. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(validationProblem(fields));
    }

    /** Invalid path, query or header values, such as a malformed Idempotency-Key. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors body) {
                // a @Valid request body, validated together with a constrained header
                body.getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
                return;
            }
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                    .forEach(error -> fields.putIfAbsent(name, error.getDefaultMessage()));
        });
        return ResponseEntity.badRequest().body(validationProblem(fields));
    }

    private static ProblemDetail validationProblem(Map<String, String> fields) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Some fields are missing or invalid.");
        problem.setTitle("Invalid request");
        problem.setType(URI.create(TYPE_BASE + "validation-failed"));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("fields", fields);
        return problem;
    }
}

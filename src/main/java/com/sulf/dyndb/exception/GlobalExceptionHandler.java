package com.sulf.dyndb.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, RuntimeException exception) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message, Instant.now()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFount(PaymentNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", exception);
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(
            InvalidPaymentStateException exception
    ) {
        return error(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", exception);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException exception
    ) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception);
    }

    @ExceptionHandler(ConcurrentPaymentModificationException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentModification(
            ConcurrentPaymentModificationException exception
    ) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_PAYMENT_MODIFICATION", exception);
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCursor(
            InvalidCursorException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception);
    }

    @ExceptionHandler(InvalidPaymentPeriodException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPeriod(InvalidPaymentPeriodException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_PERIOD", exception);
    }

}

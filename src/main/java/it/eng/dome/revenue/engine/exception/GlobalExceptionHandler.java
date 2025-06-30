package it.eng.dome.revenue.engine.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.toList());

        var body = new ValidationErrorResponse("Validation failed", errors);

        return ResponseEntity.badRequest().body(body);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormatEx && invalidFormatEx.getTargetType().isEnum()) {
            String invalidValue = invalidFormatEx.getValue().toString();
            String enumType = invalidFormatEx.getTargetType().getSimpleName();
            String msg = String.format("Invalid value '%s' for enum %s", invalidValue, enumType);
            return ResponseEntity.badRequest().body(
                Map.of(
                    "status", "error",
                    "message", msg
                )
            );
        }
        // fallback generico
        return ResponseEntity.badRequest().body(
            Map.of(
                "status", "error",
                "message", ex.getMessage()
            )
        );
    }

    // DTO interno per la risposta
    public record ValidationErrorResponse(String message, Object details) {}
}

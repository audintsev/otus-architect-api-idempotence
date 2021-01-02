package me.udintsev.otus.architect.eventing.order;

import me.udintsev.otus.architect.eventing.order.service.FingerprintMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class RestControllerExceptionHandlers extends ResponseEntityExceptionHandler {
    @ExceptionHandler(FingerprintMismatchException.class)
    protected ResponseEntity<Map<String, String>> handleConflict(FingerprintMismatchException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", "fingerprint doesn't match"));
    }
}

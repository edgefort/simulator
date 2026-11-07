package com.edgefort.simulator.admin.api;

import com.edgefort.simulator.core.scenario.SimulationControlException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error responses for the simulator control plane only. Provider modules render their own documented errors.
 */
@RestControllerAdvice(basePackageClasses = AdminController.class)
public class AdminExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(Map.of(
                "error", "VALIDATION_ERROR",
                "fields", fields
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_REQUEST_BODY",
                "message", "Request body is missing or malformed"
        ));
    }

    @ExceptionHandler(SimulationControlException.class)
    ResponseEntity<Map<String, String>> control(SimulationControlException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_SIMULATION_CONTROL",
                "message", exception.getMessage()
        ));
    }
}

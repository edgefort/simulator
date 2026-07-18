package com.edgefort.simulator.infrastructure.web;

import com.edgefort.simulator.core.application.SimulationCapacityException;
import com.edgefort.simulator.core.scenario.UnknownScenarioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler({UnknownScenarioException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_SIMULATION_REQUEST",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(SimulationCapacityException.class)
    ResponseEntity<Map<String, String>> capacity(SimulationCapacityException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "SIMULATOR_CAPACITY_REACHED",
                "message", exception.getMessage()
        ));
    }
}
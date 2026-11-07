package com.edgefort.simulator.infrastructure.web;

import com.edgefort.simulator.core.application.SimulatedTimeoutException;
import com.edgefort.simulator.core.application.SimulationCapacityException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Transport-level simulator behavior shared by every provider. It never writes a provider or simulator body.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimulationExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SimulationExceptionHandler.class);

    @ExceptionHandler(SimulatedTimeoutException.class)
    void timeout(SimulatedTimeoutException exception, HttpServletResponse response) {
        logger.info("{}; closing connection without a response", exception.getMessage());
        ConnectionTerminator.closeWithoutResponse(response);
    }

    @ExceptionHandler(SimulationCapacityException.class)
    ResponseEntity<Void> capacity(SimulationCapacityException exception) {
        logger.warn("Simulator capacity reached: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}

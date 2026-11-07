package com.edgefort.simulator.provider.nibss.nip.control;

import com.edgefort.simulator.provider.nibss.nip.application.NibssNipInwardException;
import com.edgefort.simulator.provider.nibss.nip.security.SsmException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Control-plane errors of the NIBSS admin endpoints, in the admin error shape ({@code error}, {@code message}).
 * Never a NIP message.
 */
@RestControllerAdvice(assignableTypes = NibssNipAdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class NibssNipAdminExceptionHandler {

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
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "Request body is missing or malformed");
    }

    @ExceptionHandler(SsmException.class)
    ResponseEntity<Map<String, String>> invalidKey(SsmException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_KEY", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(NibssNipInwardException.class)
    ResponseEntity<Map<String, Object>> inward(NibssNipInwardException exception) {
        if (exception.reason() == NibssNipInwardException.Reason.NOT_CONFIGURED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INWARD_NOT_CONFIGURED",
                    "message", exception.getMessage()
            ));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INWARD_CALL_FAILED");
        body.put("message", exception.getMessage());
        NibssNipInwardException.Exchange exchange = exception.exchange();
        if (exchange != null) {
            body.put("sessionId", exchange.sessionId());
            body.put("method", exchange.method());
            body.put("url", exchange.url());
            body.put("requestXml", exchange.requestXml());
            body.put("httpStatus", exchange.httpStatus());
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}

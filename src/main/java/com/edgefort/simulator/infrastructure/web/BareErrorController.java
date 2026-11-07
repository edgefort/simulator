package com.edgefort.simulator.infrastructure.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Container-level errors (unmatched routes, unsupported methods, uncaught exceptions) carry only an HTTP status.
 * Spring Boot's default error JSON would be a simulator-invented body on a provider surface; provider modules render
 * every error they can attribute to their own contract.
 */
@RestController
public class BareErrorController implements ErrorController {

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<Void> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus resolved = status instanceof Integer code
                ? HttpStatus.resolve(code)
                : null;
        return ResponseEntity.status(resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved).build();
    }
}

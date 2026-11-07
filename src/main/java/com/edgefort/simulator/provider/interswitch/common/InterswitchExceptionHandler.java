package com.edgefort.simulator.provider.interswitch.common;

import com.edgefort.simulator.provider.interswitch.InterswitchApiException;
import com.edgefort.simulator.provider.interswitch.InterswitchProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Renders every client-visible Interswitch rejection with the Quickteller v5 response envelope
 * ({@code ResponseCode}, {@code ResponseDescription}, {@code ResponseCodeGrouping}) and codes from the published
 * response-code list. Malformed or incomplete requests use {@code 10002} "Invalid Request".
 */
@RestControllerAdvice(basePackageClasses = InterswitchProvider.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InterswitchExceptionHandler {

    private static final String FAILED = "FAILED";

    @ExceptionHandler(InterswitchApiException.class)
    ResponseEntity<InterswitchErrorResponse> rejected(InterswitchApiException exception) {
        return ResponseEntity.status(exception.status()).body(new InterswitchErrorResponse(
                exception.responseCode(), exception.responseDescription(), FAILED
        ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<InterswitchErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new InterswitchErrorResponse(
                "10002", "Invalid Request", FAILED
        ));
    }

    record InterswitchErrorResponse(String ResponseCode, String ResponseDescription, String ResponseCodeGrouping) {
    }
}

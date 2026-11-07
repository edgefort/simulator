package com.edgefort.simulator.provider.onafriq.billpayment.api;

import com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.Response;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.RecordComponent;
import java.util.List;

/**
 * Renders every client-visible Onafriq error in the documented response object
 * ({@code {"status":"error","message":...,"code":...}}) with documented codes: 401 for authentication, 422 with
 * {@code BX0003} for request field validation, and 400 with {@code BX0003} for unreadable or non-JSON bodies.
 */
@RestControllerAdvice(basePackageClasses = OnafriqBillPaymentProvider.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class OnafriqExceptionHandler {

    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    @ExceptionHandler(OnafriqAuthenticationException.class)
    ResponseEntity<Response> authentication(OnafriqAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Response.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Response> validation(MethodArgumentNotValidException exception) {
        Class<?> target = exception.getParameter().getParameterType();
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldName(target, error) + " " + error.getDefaultMessage())
                .distinct()
                .toList();
        return invalid(UNPROCESSABLE, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Response> missingParameter(MissingServletRequestParameterException exception) {
        return invalid(UNPROCESSABLE, List.of(exception.getParameterName() + " is required"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<Response> unreadable(Exception exception) {
        return invalid(HttpStatus.BAD_REQUEST, null);
    }

    private static ResponseEntity<Response> invalid(HttpStatusCode status, List<String> errors) {
        return ResponseEntity.status(status).body(Response.error(
                OnafriqBillPaymentContracts.CODE_INVALID_REQUEST,
                OnafriqBillPaymentContracts.MESSAGE_INVALID_REQUEST,
                errors
        ));
    }

    /**
     * Reports the documented JSON name ({@code service_type}) instead of the Java component name of the first path
     * segment; nested Onafriq objects already use their documented camel-case names.
     */
    private static String fieldName(Class<?> target, FieldError error) {
        String path = error.getField();
        int separator = path.indexOf('.');
        String head = separator < 0 ? path : path.substring(0, separator);
        String tail = separator < 0 ? "" : path.substring(separator);
        RecordComponent[] components = target.getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
                if (component.getName().equals(head) && property != null && !property.value().isEmpty()) {
                    return property.value() + tail;
                }
            }
        }
        return path;
    }
}

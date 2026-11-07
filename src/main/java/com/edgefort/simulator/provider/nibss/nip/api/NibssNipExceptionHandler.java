package com.edgefort.simulator.provider.nibss.nip.api;

import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.FORMAT_ERROR;

/**
 * Answers requests that cannot be processed with the operation's own NIP response message carrying response code
 * {@code 30} (format error). Fields the request supplied are echoed as in a normal response. This covers missing or
 * invalid elements, unreadable XML (including documents with a DTD or external entity, which the XML reader
 * rejects), and non-XML content types. The SOAP transport builds the same message through
 * {@link #responseMessage(Class, Object)} and returns it encrypted.
 */
@RestControllerAdvice(assignableTypes = NibssNipController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class NibssNipExceptionHandler {

    private static final String RESPONSE_CODE_COMPONENT = "responseCode";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> invalidMessage(MethodArgumentNotValidException exception, HandlerMethod handler) {
        return formatError(handler, exception.getBindingResult().getTarget());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<Object> unreadableMessage(Exception exception, HandlerMethod handler) {
        return formatError(handler, null);
    }

    private static ResponseEntity<Object> formatError(HandlerMethod handler, Object request) {
        Class<?> responseType = ResolvableType.forMethodReturnType(handler.getMethod())
                .as(ResponseEntity.class)
                .getGeneric(0)
                .resolve();
        if (responseType == null || !responseType.isRecord()) {
            throw new IllegalStateException("NIP handler %s must return ResponseEntity of a NIP response message"
                    .formatted(handler.getShortLogMessage()));
        }
        return NibssNipController.xml(responseMessage(responseType, request));
    }

    /**
     * Builds the format-error response message, echoing request elements that share a name with a response element.
     */
    static Object responseMessage(Class<?> responseType, Object request) {
        Map<String, Object> requestValues = new HashMap<>();
        if (request != null && request.getClass().isRecord()) {
            for (RecordComponent component : request.getClass().getRecordComponents()) {
                requestValues.put(component.getName(), invoke(component, request));
            }
        }
        RecordComponent[] components = responseType.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            types[index] = component.getType();
            Object value = RESPONSE_CODE_COMPONENT.equals(component.getName())
                    ? FORMAT_ERROR
                    : requestValues.get(component.getName());
            values[index] = component.getType().isInstance(value) ? value : null;
        }
        try {
            return responseType.getDeclaredConstructor(types).newInstance(values);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create NIP response " + responseType.getName(), exception);
        }
    }

    private static Object invoke(RecordComponent component, Object record) {
        try {
            return component.getAccessor().invoke(record);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read NIP request element " + component.getName(), exception);
        }
    }
}

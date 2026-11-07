package com.edgefort.simulator.infrastructure.web;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.coyote.ActionCode;

import java.lang.reflect.Field;

/**
 * Closes the client connection without writing a status line, headers, or body. This is what a client observes when
 * a provider never answers. Requires the embedded Tomcat connector.
 */
final class ConnectionTerminator {

    private static final Field CATALINA_RESPONSE = catalinaResponseField();

    private ConnectionTerminator() {
    }

    static void closeWithoutResponse(ServletResponse response) {
        ServletResponse current = response;
        while (current instanceof ServletResponseWrapper wrapper) {
            current = wrapper.getResponse();
        }
        if (!(current instanceof ResponseFacade facade)) {
            throw new IllegalStateException(
                    "Simulated timeouts require the embedded Tomcat connector, found " + current.getClass().getName()
            );
        }
        try {
            Response catalinaResponse = (Response) CATALINA_RESPONSE.get(facade);
            catalinaResponse.getCoyoteResponse().action(ActionCode.CLOSE_NOW, null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to close the Tomcat connection", exception);
        }
    }

    private static Field catalinaResponseField() {
        try {
            Field field = ResponseFacade.class.getDeclaredField("response");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

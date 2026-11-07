package com.edgefort.simulator.support;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that the server closes the connection without sending a single byte, which is what a client observes when a
 * provider never answers. MockMvc cannot observe a dropped connection, so timeout tests must run against
 * {@code webEnvironment = RANDOM_PORT}.
 *
 * <p>A raw socket is used because HTTP clients, including the JDK client, transparently retry idempotent requests
 * after an empty reply, which would hide the dropped connection.</p>
 */
public final class NoResponseAssertions {

    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private NoResponseAssertions() {
    }

    public static void assertNoResponse(String method, URI uri, Map<String, String> headers, String body) {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder request = new StringBuilder()
                .append(method).append(' ').append(uri.getRawPath())
                .append(uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                .append(" HTTP/1.1\r\n")
                .append("Host: ").append(uri.getHost()).append(':').append(uri.getPort()).append("\r\n")
                .append("Connection: close\r\n")
                .append("Content-Length: ").append(payload.length).append("\r\n");
        headers.forEach((name, value) -> request.append(name).append(": ").append(value).append("\r\n"));
        request.append("\r\n");

        try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream output = socket.getOutputStream();
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            output.write(payload);
            output.flush();
            InputStream input = socket.getInputStream();
            int first;
            try {
                first = input.read();
            } catch (SocketException reset) {
                first = -1;
            }
            assertThat(first)
                    .as("%s %s must close the connection without sending any bytes", method, uri)
                    .isEqualTo(-1);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to exercise " + method + " " + uri, exception);
        }
    }
}

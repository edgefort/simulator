package com.edgefort.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Container-level responses on provider surfaces carry no simulator-invented body.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "simulator.providers.onafriq-bill-payment.enabled=false"
)
class ProviderSurfaceIntegrationTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void disabledProviderRoutesAreNotFoundWithoutBody() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/services/airtime/request"))
                .header("x-api-key", "simulator-api-key")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void unmatchedRoutesAreNotFoundWithoutBody() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/quicktellerservice/api/v5/unknown"))
                .GET());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

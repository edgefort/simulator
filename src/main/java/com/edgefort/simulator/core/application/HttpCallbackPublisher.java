package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.state.SimulatedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HttpCallbackPublisher implements CallbackPublisher {

    private static final Logger logger = LoggerFactory.getLogger(HttpCallbackPublisher.class);

    private final RestClient restClient;
    private final Set<String> allowedHosts;

    public HttpCallbackPublisher(RestClient restClient, List<String> allowedHosts) {
        this.restClient = restClient;
        this.allowedHosts = new HashSet<>(allowedHosts);
    }

    @Override
    public void publish(URI callbackUrl, SimulatedTransaction transaction) {
        validate(callbackUrl);
        try {
            restClient.post()
                    .uri(callbackUrl)
                    .body(new CallbackEvent(
                            transaction.key().provider(),
                            transaction.key().reference(),
                            transaction.status().name(),
                            transaction.updatedAt()
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            logger.warn(
                    "Simulator callback failed for provider={} reference={}: {}",
                    transaction.key().provider(),
                    transaction.key().reference(),
                    exception.getMessage()
            );
        }
    }

    @Override
    public void validate(URI callbackUrl) {
        if (!"http".equals(callbackUrl.getScheme()) && !"https".equals(callbackUrl.getScheme())) {
            throw new IllegalArgumentException("Callback URL must use HTTP or HTTPS");
        }
        if (!allowedHosts.contains(callbackUrl.getHost())) {
            throw new IllegalArgumentException("Callback host is not allowed by simulator configuration");
        }
    }

    public record CallbackEvent(
            String provider,
            String reference,
            String status,
            java.time.Instant occurredAt
    ) {
    }
}
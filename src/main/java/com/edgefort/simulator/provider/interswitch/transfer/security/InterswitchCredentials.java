package com.edgefort.simulator.provider.interswitch.transfer.security;

import com.edgefort.simulator.provider.interswitch.transfer.config.InterswitchTransferProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class InterswitchCredentials {

    private final InterswitchTransferProperties properties;

    public InterswitchCredentials(InterswitchTransferProperties properties) {
        this.properties = properties;
    }

    public boolean acceptsBasicAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(authorization.substring("Basic ".length())),
                    StandardCharsets.UTF_8
            );
            return constantTimeEquals(
                    decoded,
                    properties.getClientId() + ":" + properties.getClientSecret()
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean acceptsBearerAuthorization(String authorization) {
        return authorization != null
                && authorization.startsWith("Bearer ")
                && constantTimeEquals(authorization.substring("Bearer ".length()), properties.getAccessToken());
    }

    public String accessToken() {
        return properties.getAccessToken();
    }

    public long expiresIn() {
        return properties.getTokenExpiresIn();
    }

    private boolean constantTimeEquals(String actual, String expected) {
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}
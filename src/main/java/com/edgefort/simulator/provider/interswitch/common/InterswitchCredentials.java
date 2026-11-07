package com.edgefort.simulator.provider.interswitch.common;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class InterswitchCredentials {

    private final InterswitchProperties properties;

    public InterswitchCredentials(InterswitchProperties properties) {
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
            return constantTimeEquals(decoded, properties.getClientId() + ":" + properties.getClientSecret());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean acceptsBearerToken(String token) {
        return constantTimeEquals(token, properties.getAccessToken());
    }

    public boolean acceptsTerminal(String terminalId) {
        return properties.getTerminalId().equals(terminalId);
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

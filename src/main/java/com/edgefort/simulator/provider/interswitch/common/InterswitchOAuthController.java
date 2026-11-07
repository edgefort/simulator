package com.edgefort.simulator.provider.interswitch.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Interswitch Passport client-credentials token endpoint. Errors use the OAuth 2.0 (RFC 6749 section 5.2) error body
 * because the public Interswitch guides document only the successful token response.
 */
@RestController
@Tag(name = "Interswitch Authentication", description = "Interswitch Passport OAuth client-credentials token")
public class InterswitchOAuthController {

    private final InterswitchCredentials credentials;

    public InterswitchOAuthController(InterswitchCredentials credentials) {
        this.credentials = credentials;
    }

    @Operation(summary = "Generate an access token")
    @PostMapping(path = "/passport/oauth/token", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "grant_type", required = false) String grantType
    ) {
        if (!credentials.acceptsBasicAuthorization(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_client"));
        }
        if (grantType == null || grantType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
        }
        if (!"client_credentials".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        return ResponseEntity.ok(new AccessTokenResponse(
                credentials.accessToken(),
                "bearer",
                credentials.expiresIn()
        ));
    }

    record AccessTokenResponse(String access_token, String token_type, long expires_in) {
    }
}

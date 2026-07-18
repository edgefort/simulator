package com.edgefort.simulator.provider.interswitch.transfer.api;

import com.edgefort.simulator.provider.interswitch.transfer.security.InterswitchCredentials;
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

@RestController
@Tag(name = "Interswitch Authentication", description = "Interswitch OAuth client-credentials simulation")
public class InterswitchOAuthController {

    private final InterswitchCredentials credentials;

    public InterswitchOAuthController(InterswitchCredentials credentials) {
        this.credentials = credentials;
    }

    @Operation(summary = "Generate an access token")
    @PostMapping(
            path = "/passport/oauth/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<?> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("grant_type") String grantType
    ) {
        if (!credentials.acceptsBasicAuthorization(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_client"));
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
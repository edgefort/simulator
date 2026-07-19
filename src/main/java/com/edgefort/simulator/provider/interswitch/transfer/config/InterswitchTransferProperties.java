package com.edgefort.simulator.provider.interswitch.transfer.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("simulator.providers.interswitch-transfer.config")
public class InterswitchTransferProperties {

    @NotBlank
    private String clientId = "simulator-client";

    @NotBlank
    private String clientSecret = "simulator-secret";

    @NotBlank
    private String accessToken = "simulator-access-token";

    @Positive
    private long tokenExpiresIn = 86_400;

    @NotBlank
    private String terminalId = "3PBL0001";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public long getTokenExpiresIn() {
        return tokenExpiresIn;
    }

    public void setTokenExpiresIn(long tokenExpiresIn) {
        this.tokenExpiresIn = tokenExpiresIn;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }
}
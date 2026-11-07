package com.edgefort.simulator.provider.nibss.nip.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * NIBSS NIP module settings. Key values are Base64 encodings of OpenPGP key rings (ASCII-armored or binary) and
 * must come from the environment, never from source control.
 */
@Validated
@ConfigurationProperties("simulator.providers.nibss-nip.config")
public class NibssNipProperties {

    /** Simulator (NIBSS-side) secret key ring. Empty: a new RSA-2048 key pair is generated on every startup. */
    @NotNull
    private String simulatorPrivateKey = "";

    @NotNull
    private String simulatorPrivateKeyPassphrase = "";

    /** Public key ring of the connected institution. Empty: upload it through the admin API. */
    @NotNull
    private String clientPublicKey = "";

    @Valid
    @NotNull
    private Inward inward = new Inward();

    public String getSimulatorPrivateKey() {
        return simulatorPrivateKey;
    }

    public void setSimulatorPrivateKey(String simulatorPrivateKey) {
        this.simulatorPrivateKey = simulatorPrivateKey;
    }

    public String getSimulatorPrivateKeyPassphrase() {
        return simulatorPrivateKeyPassphrase;
    }

    public void setSimulatorPrivateKeyPassphrase(String simulatorPrivateKeyPassphrase) {
        this.simulatorPrivateKeyPassphrase = simulatorPrivateKeyPassphrase;
    }

    public String getClientPublicKey() {
        return clientPublicKey;
    }

    public void setClientPublicKey(String clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }

    public Inward getInward() {
        return inward;
    }

    public void setInward(Inward inward) {
        this.inward = inward;
    }

    /**
     * The institution's inward NIP web service, which the simulator calls when it plays NIBSS. Institution WSDLs
     * differ, so the operation namespace, request element, and SOAP action are configurable.
     */
    public static class Inward {

        /** Endpoint URL of the institution's NIP web service. Empty: inward calls are rejected. */
        @NotNull
        private String url = "";

        @NotBlank
        private String namespace = "http://core.nip.nibss/";

        @NotBlank
        private String requestElement = "request";

        @NotNull
        private String soapAction = "";

        @NotNull
        private Duration timeout = Duration.ofSeconds(30);

        /** Sender code used in characters 1-6 of generated session IDs. */
        @Pattern(regexp = "\\d{6}", message = "source-institution-code must be six digits")
        private String sourceInstitutionCode = "999999";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getRequestElement() {
            return requestElement;
        }

        public void setRequestElement(String requestElement) {
            this.requestElement = requestElement;
        }

        public String getSoapAction() {
            return soapAction;
        }

        public void setSoapAction(String soapAction) {
            this.soapAction = soapAction;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public String getSourceInstitutionCode() {
            return sourceInstitutionCode;
        }

        public void setSourceInstitutionCode(String sourceInstitutionCode) {
            this.sourceInstitutionCode = sourceInstitutionCode;
        }

        @AssertTrue(message = "inward.timeout must be positive")
        public boolean isTimeoutPositive() {
            return timeout != null && timeout.isPositive();
        }

        @AssertTrue(message = "inward.url must be empty or an absolute http(s) URL")
        public boolean isUrlValid() {
            if (url == null || url.isEmpty()) {
                return true;
            }
            try {
                URI uri = new URI(url);
                return uri.getHost() != null
                        && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
            }
            catch (URISyntaxException exception) {
                return false;
            }
        }
    }
}

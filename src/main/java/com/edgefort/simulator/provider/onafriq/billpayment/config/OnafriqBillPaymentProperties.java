package com.edgefort.simulator.provider.onafriq.billpayment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("simulator.providers.onafriq-bill-payment.config")
public class OnafriqBillPaymentProperties {

    @NotBlank
    private String apiKey = "simulator-api-key";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
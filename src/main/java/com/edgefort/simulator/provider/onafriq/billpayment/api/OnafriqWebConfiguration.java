package com.edgefort.simulator.provider.onafriq.billpayment.api;

import com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider;
import com.edgefort.simulator.provider.onafriq.billpayment.config.OnafriqBillPaymentProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class OnafriqWebConfiguration implements WebMvcConfigurer {

    private final OnafriqBillPaymentProvider provider;
    private final OnafriqBillPaymentProperties properties;

    OnafriqWebConfiguration(OnafriqBillPaymentProvider provider, OnafriqBillPaymentProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OnafriqAuthenticationInterceptor(provider, properties));
    }
}

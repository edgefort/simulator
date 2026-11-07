package com.edgefort.simulator.provider.interswitch.common;

import com.edgefort.simulator.provider.interswitch.InterswitchProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterswitchWebConfiguration implements WebMvcConfigurer {

    private final InterswitchProvider provider;
    private final InterswitchCredentials credentials;

    public InterswitchWebConfiguration(InterswitchProvider provider, InterswitchCredentials credentials) {
        this.provider = provider;
        this.credentials = credentials;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InterswitchAuthorizationInterceptor(provider, credentials));
    }
}

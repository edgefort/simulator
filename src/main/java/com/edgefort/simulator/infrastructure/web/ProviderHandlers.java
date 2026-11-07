package com.edgefort.simulator.infrastructure.web;

import com.edgefort.simulator.core.provider.SimulatedProvider;
import org.springframework.web.method.HandlerMethod;

/**
 * Lets a provider scope its interceptors to its own request handlers instead of URL prefixes, so authentication
 * follows the provider when it is mounted under a base path.
 */
public final class ProviderHandlers {

    private ProviderHandlers() {
    }

    public static boolean ownedBy(SimulatedProvider provider, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return false;
        }
        String packageName = method.getBeanType().getPackageName();
        return packageName.equals(provider.basePackage()) || packageName.startsWith(provider.basePackage() + ".");
    }
}

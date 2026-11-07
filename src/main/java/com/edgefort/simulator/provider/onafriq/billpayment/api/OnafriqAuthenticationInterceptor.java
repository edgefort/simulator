package com.edgefort.simulator.provider.onafriq.billpayment.api;

import com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider;
import com.edgefort.simulator.provider.onafriq.billpayment.config.OnafriqBillPaymentProperties;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts;
import com.edgefort.simulator.infrastructure.web.ProviderHandlers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Documented API-key authentication: {@code x-api-key: <key>} or {@code Authorization: Api-key <key>}. Scoped to
 * Onafriq handlers, so it applies wherever the provider is mounted. Failures are rendered by
 * {@link OnafriqExceptionHandler}.
 */
class OnafriqAuthenticationInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "x-api-key";
    private static final String AUTHORIZATION_PREFIX = "Api-key ";

    private final OnafriqBillPaymentProvider provider;
    private final OnafriqBillPaymentProperties properties;

    OnafriqAuthenticationInterceptor(OnafriqBillPaymentProvider provider, OnafriqBillPaymentProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!ProviderHandlers.ownedBy(provider, handler)) {
            return true;
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (isBlank(apiKey) && isBlank(authorization)) {
            throw new OnafriqAuthenticationException(
                    OnafriqBillPaymentContracts.CODE_CREDENTIALS_MISSING,
                    OnafriqBillPaymentContracts.MESSAGE_CREDENTIALS_MISSING
            );
        }
        boolean authenticated = (!isBlank(apiKey) && matches(apiKey))
                || (authorization != null
                && authorization.regionMatches(true, 0, AUTHORIZATION_PREFIX, 0, AUTHORIZATION_PREFIX.length())
                && matches(authorization.substring(AUTHORIZATION_PREFIX.length())));
        if (!authenticated) {
            throw new OnafriqAuthenticationException(
                    OnafriqBillPaymentContracts.CODE_BAD_CREDENTIALS,
                    OnafriqBillPaymentContracts.MESSAGE_BAD_CREDENTIALS
            );
        }
        return true;
    }

    private boolean matches(String suppliedKey) {
        return MessageDigest.isEqual(
                properties.getApiKey().getBytes(StandardCharsets.UTF_8),
                suppliedKey.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

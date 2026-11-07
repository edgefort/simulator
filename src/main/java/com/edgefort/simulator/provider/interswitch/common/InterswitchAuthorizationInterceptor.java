package com.edgefort.simulator.provider.interswitch.common;

import com.edgefort.simulator.infrastructure.web.ProviderHandlers;
import com.edgefort.simulator.provider.interswitch.InterswitchApiException;
import com.edgefort.simulator.provider.interswitch.InterswitchProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires the bearer token issued by the Passport endpoint and the configured {@code TerminalId} header on every
 * Quickteller v5 handler of this provider, wherever the provider is mounted. Rejections are rendered by
 * {@link InterswitchExceptionHandler}.
 */
class InterswitchAuthorizationInterceptor implements HandlerInterceptor {

    static final String TERMINAL_ID_HEADER = "TerminalId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final InterswitchProvider provider;
    private final InterswitchCredentials credentials;

    InterswitchAuthorizationInterceptor(InterswitchProvider provider, InterswitchCredentials credentials) {
        this.provider = provider;
        this.credentials = credentials;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!ProviderHandlers.ownedBy(provider, handler)
                || ((HandlerMethod) handler).getBeanType() == InterswitchOAuthController.class) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            throw new InterswitchApiException(HttpStatus.UNAUTHORIZED, "10003", "Unauthorized");
        }
        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || !credentials.acceptsBearerToken(authorization.substring(BEARER_PREFIX.length()))) {
            throw new InterswitchApiException(
                    HttpStatus.UNAUTHORIZED, "60003", "Security token invalid or expired."
            );
        }
        String terminalId = request.getHeader(TERMINAL_ID_HEADER);
        if (terminalId == null || terminalId.isBlank()) {
            throw new InterswitchApiException(HttpStatus.BAD_REQUEST, "20021", "TerminalId/ClientId is required");
        }
        if (!credentials.acceptsTerminal(terminalId)) {
            throw new InterswitchApiException(HttpStatus.BAD_REQUEST, "70012", "Unrecognized terminal owner");
        }
        return true;
    }
}

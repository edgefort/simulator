package com.edgefort.simulator.provider.onafriq.billpayment.api;

import com.edgefort.simulator.provider.onafriq.billpayment.config.OnafriqBillPaymentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OnafriqAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "x-api-key";
    private static final String AUTHORIZATION_PREFIX = "Api-key ";

    private final OnafriqBillPaymentProperties properties;

    public OnafriqAuthenticationFilter(OnafriqBillPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/services/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedKey = request.getHeader(API_KEY_HEADER);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if ((suppliedKey == null || suppliedKey.isBlank())
                && (authorization == null || authorization.isBlank())) {
            reject(response, "BX0013", "Api key is required");
            return;
        }
        if ((suppliedKey != null && matches(suppliedKey))
                || (authorization != null
                && authorization.startsWith(AUTHORIZATION_PREFIX)
                && matches(authorization.substring(AUTHORIZATION_PREFIX.length())))) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(response, "SEC00001", "Invalid api key");
    }

    private boolean matches(String suppliedKey) {
        return MessageDigest.isEqual(
                properties.getApiKey().getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":"error","code":"%s","message":"%s","data":{},"errors":["%s"]}
                """.formatted(code, message, message));
    }
}
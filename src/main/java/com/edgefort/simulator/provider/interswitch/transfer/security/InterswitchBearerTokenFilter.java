package com.edgefort.simulator.provider.interswitch.transfer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InterswitchBearerTokenFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/quicktellerservice/api/v5/";

    private final InterswitchCredentials credentials;

    public InterswitchBearerTokenFilter(InterswitchCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!credentials.acceptsBearerAuthorization(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
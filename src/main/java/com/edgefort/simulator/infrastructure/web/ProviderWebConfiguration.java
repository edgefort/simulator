package com.edgefort.simulator.infrastructure.web;

import com.edgefort.simulator.core.provider.ProviderRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mounts each provider at its specification paths, optionally behind {@code simulator.providers.<id>.base-path},
 * and hides disabled providers. Spring MVC fails startup when two providers map the same route.
 *
 * <p>Handlers annotated with {@link ControlPlaneEndpoint} belong to the control plane even when they live in a
 * provider package: they keep their {@code /admin/...} paths, so the admin token always protects them, and stay
 * available when the provider is disabled.</p>
 */
@Configuration
public class ProviderWebConfiguration implements WebMvcConfigurer {

    private final ProviderRegistry registry;

    public ProviderWebConfiguration(ProviderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        registry.providers().forEach(provider -> {
            String basePath = registry.settings(provider.id()).getBasePath();
            if (!basePath.isEmpty()) {
                configurer.addPathPrefix(basePath, HandlerTypePredicate.forBasePackage(provider.basePackage())
                        .and(type -> !isControlPlane(type)));
            }
        });
    }

    @Override
    public void addInterceptors(InterceptorRegistry interceptors) {
        interceptors.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method && !isControlPlane(method.getBeanType())) {
                    boolean disabled = registry.owner(method.getBeanType())
                            .map(provider -> !registry.settings(provider.id()).isEnabled())
                            .orElse(false);
                    if (disabled) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        return false;
                    }
                }
                return true;
            }
        }).order(Ordered.HIGHEST_PRECEDENCE);
    }

    private static boolean isControlPlane(Class<?> handlerType) {
        return AnnotatedElementUtils.hasAnnotation(handlerType, ControlPlaneEndpoint.class);
    }
}

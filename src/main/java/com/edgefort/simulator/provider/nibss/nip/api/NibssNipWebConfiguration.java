package com.edgefort.simulator.provider.nibss.nip.api;

import com.edgefort.simulator.infrastructure.web.ProviderHandlers;
import com.edgefort.simulator.provider.nibss.nip.NibssNipProvider;
import io.swagger.v3.oas.models.media.Content;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Accepts only {@code application/xml} NIP messages. The check runs after a NIBSS handler is selected, so
 * {@link NibssNipExceptionHandler} can answer a wrong media type with a NIP response instead of a generic error.
 */
@Configuration
public class NibssNipWebConfiguration implements WebMvcConfigurer {

    private final NibssNipProvider provider;

    public NibssNipWebConfiguration(NibssNipProvider provider) {
        this.provider = provider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws HttpMediaTypeNotSupportedException {
                if (ProviderHandlers.ownedBy(provider, handler) && !isXml(request.getContentType())) {
                    throw new HttpMediaTypeNotSupportedException(
                            "NIP messages must be sent as application/xml", List.of(MediaType.APPLICATION_XML)
                    );
                }
                return true;
            }
        });
    }

    /**
     * Documents NIBSS request and response bodies as XML, matching what the handlers accept and return.
     */
    @Bean
    OperationCustomizer nibssNipXmlMessages() {
        return (operation, handlerMethod) -> {
            if (ProviderHandlers.ownedBy(provider, handlerMethod)) {
                if (operation.getRequestBody() != null) {
                    xmlOnly(operation.getRequestBody().getContent());
                }
                if (operation.getResponses() != null) {
                    operation.getResponses().values().forEach(response -> xmlOnly(response.getContent()));
                }
            }
            return operation;
        };
    }

    private static void xmlOnly(Content content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        io.swagger.v3.oas.models.media.MediaType body = content.values().iterator().next();
        content.clear();
        content.addMediaType(MediaType.APPLICATION_XML_VALUE, body);
    }

    private static boolean isXml(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_XML.includes(MediaType.parseMediaType(contentType));
        }
        catch (InvalidMediaTypeException exception) {
            return false;
        }
    }
}

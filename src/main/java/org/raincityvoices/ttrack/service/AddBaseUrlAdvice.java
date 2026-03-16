package org.raincityvoices.ttrack.service;

import java.util.List;
import java.util.Set;

import org.raincityvoices.ttrack.service.api.UriContainer;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.collect.ImmutableSet;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link ResponseBodyAdvice} that allows response objects to provide back-links
 * to the service that have the correct client-facing base URL, i.e. the one used
 * by the client when sending the request. Note that this base URL is tricky to
 * obtain at the container level, where the original HTTP request isn't available.
 * 
 * Response objects that contain such URLs should implement the interface
 * {@link UriContainer}. The advice object checks whether the response is such
 * an object, or - if it's a list - whether its elements are, and if so
 * invokes {@link UriContainer#useBaseUrl(String) useBaseUrl}, passing the base URL.
 * 
 * Note: The advice applies only to responses with JSON/YAML/HTML media type,
 * since those are the only ones likely to contain URLs in the first place.
 */
@Slf4j
@ControllerAdvice
public class AddBaseUrlAdvice implements ResponseBodyAdvice<Object> {

    private static final Set<MediaType> CONTENT_TYPES = ImmutableSet.of(
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_YAML,
        MediaType.TEXT_HTML
    );

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (!CONTENT_TYPES.contains(selectedContentType)) {
            log.debug("Skipping content type: {}", selectedContentType);
            return body;
        }
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        log.debug("Base URL: {}", baseUrl);
        return process(body, baseUrl);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object process(Object original, String baseUrl) {
        if (original instanceof List) {
            return ((List)original).stream().map(e -> process(e, baseUrl)).toList();
        }
        if (!(original instanceof UriContainer)) {
            return original;
        }
        return ((UriContainer)original).useBaseUrl(baseUrl);
    }
}

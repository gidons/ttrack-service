package org.raincityvoices.ttrack.service.api;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

public interface UriContainer<T> {

    T useBaseUrl(String baseUrl);

    default URI rewriteUri(URI original, String baseUrl) {
        if (original == null || original.getScheme() != null) {
            return original;
        }
        try {
            return new URI(baseUrl + original.getPath());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unexpected exception adding base URL '" + baseUrl + "' to '" + original + "'");
        }
    }
}

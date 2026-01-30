package org.raincityvoices.ttrack.service.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Builder;
import lombok.Getter;
import lombok.Builder.Default;
import lombok.experimental.NonFinal;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder(toBuilder = true)
@Jacksonized
public class Song implements UriContainer<Song> {
    @Default SongId id = SongId.NONE;
    /** Full title of the song. Recommended to be unique. */
    String title;
    /** A short title that is used as prefix to track names. */
    String shortTitle;
    /** TBA. */
    String version;
    String arranger;
    String key;
    String voicing;
    @Getter(onMethod = @__(@JsonProperty("eTag")))
    String eTag;
    // The following may or may not be included
    List<String> parts;
    URI mediaUrl;
    Instant mediaUpdated;
    URI textDataUrl;
    Instant textDataUpdated;

    @Override
    public Song useBaseUrl(String baseUrl) {
        return toBuilder()
            .mediaUrl(rewriteUri(mediaUrl, baseUrl))
            .textDataUrl(rewriteUri(textDataUrl, baseUrl))
            .build();
    }
}

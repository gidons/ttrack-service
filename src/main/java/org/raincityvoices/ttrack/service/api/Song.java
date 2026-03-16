package org.raincityvoices.ttrack.service.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
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
    URI notationUrl;
    Instant notationUpdated;

    @Override
    public Song useBaseUrl(String baseUrl) {
        return toBuilder()
            .mediaUrl(rewriteUri(mediaUrl, baseUrl))
            .textDataUrl(rewriteUri(textDataUrl, baseUrl))
            .build();
    }
}

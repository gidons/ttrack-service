package org.raincityvoices.ttrack.service.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@Value
@NonFinal
@SuperBuilder
@Accessors(fluent = true)
public abstract class AudioTrack {
    @JsonProperty("songId")
    SongId songId;
    @JsonProperty("blobName")
    String blobName;
    @JsonIgnore
    public abstract String trackId();
    @JsonProperty("created")
    Instant created;
    @JsonProperty("updated")
    Instant updated;
    @JsonIgnore
    public boolean isProcessed() { return blobName != null; }
}

package org.raincityvoices.ttrack.service.api;

import java.net.URI;
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
    @JsonIgnore
    public abstract String trackId();
    @JsonProperty("durationSec")
    int durationSec;
    @JsonProperty("created")
    Instant created;
    @JsonProperty("updated")
    Instant updated;
    @JsonProperty("url")
    public abstract URI url();
    @JsonProperty("mediaUrl")
    public abstract URI mediaUrl();
    @JsonIgnore
    public boolean isProcessed() { return mediaUrl() != null; }
}

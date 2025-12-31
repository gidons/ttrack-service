package org.raincityvoices.ttrack.service.api;

import java.net.URI;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@Value
@NonFinal
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@Getter(onMethod = @__(@JsonProperty))
public abstract class AudioTrack {
    SongId songId;
    @JsonProperty
    public abstract String trackId();
    Integer durationSec;
    Instant created;
    Instant updated;
    String currentTaskId;
    @JsonIgnore
    boolean hasMedia;
    @JsonProperty
    public abstract URI url();
    @JsonProperty
    public abstract URI mediaUrl();
}

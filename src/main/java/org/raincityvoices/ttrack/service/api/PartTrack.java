package org.raincityvoices.ttrack.service.api;

import java.net.URI;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartTrack extends AudioTrack {

    @JsonProperty
    private final AudioPart part;

    public String trackId() { return part.name(); }

    @Override
    @JsonProperty
    public URI url() { return SongController.partTrackUrl(songId(), part()); }

    @Override
    @JsonProperty
    public URI mediaUrl() { return hasMedia() ? SongController.partMediaUrl(songId(), part()) : null; }
}

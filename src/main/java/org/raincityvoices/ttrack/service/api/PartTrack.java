package org.raincityvoices.ttrack.service.api;

import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@AllArgsConstructor
@Accessors(fluent = true)
@Jacksonized // necessary because accessors aren't bean-like
public class PartTrack implements AudioTrack {

    private final SongId songId;
    private final AudioPart part;
    private final String blobName;

    @JsonIgnore
    public String trackId() {
        return part.name();
    }
}

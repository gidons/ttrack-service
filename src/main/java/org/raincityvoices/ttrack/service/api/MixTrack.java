package org.raincityvoices.ttrack.service.api;

import java.beans.Transient;
import java.util.List;

import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@AllArgsConstructor
@Accessors(fluent = true)
@Jacksonized // necessary because accessors aren't bean-like
public class MixTrack implements AudioTrack {

    private final SongId songId;
    private final String name;
    private final List<AudioPart> parts;
    private final AudioMix mix;
    @With
    private final String blobName;

    @JsonIgnore
    @Override
    public String trackId() {
        return name();
    }
}

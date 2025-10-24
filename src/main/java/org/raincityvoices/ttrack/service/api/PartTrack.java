package org.raincityvoices.ttrack.service.api;

import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@Accessors(fluent = true)
@Jacksonized // necessary because accessors aren't bean-like
public class PartTrack extends AudioTrack {

    private final AudioPart part;

    @JsonIgnore
    public String trackId() {
        return part.name();
    }
}

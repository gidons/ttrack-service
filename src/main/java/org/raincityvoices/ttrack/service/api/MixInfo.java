package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Accessors(fluent = true)
@Jacksonized // necessary because accessors aren't bean-like
public class MixInfo {

    @JsonProperty("name")
    private final String name;
    @JsonProperty("parts")
    private final List<AudioPart> parts;
    @JsonProperty("mix")
    private final AudioMix mix;

}

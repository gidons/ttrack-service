package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Builder
@Accessors(fluent = true)
@Getter(onMethod=@__(@JsonProperty()))
public class MixInfo {

    private final String name;
    private final List<AudioPart> parts;
    private final AudioMix mix;
    /** Pitch shift in half-steps, e.g. +7 is up a fifth, -3 is down a minor 3rd. */
    private final int pitchShift;
    /** Speed factor, e.g. 2 is twice as fast the original, 0.5 is twice as slow. */
    private final double speedFactor;

}

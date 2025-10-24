package org.raincityvoices.ttrack.service.audio.model;

import java.nio.FloatBuffer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableList;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@Accessors(fluent = true)
@Jacksonized
public class AllPartsMix implements AudioMix {

    ImmutableList<AudioPart> parts;

    @JsonCreator
    public AllPartsMix(Iterable<AudioPart> parts) {
        this.parts = ImmutableList.copyOf(parts);
    }

    @Override
    public int numInputs() {
        return parts.size();
    }

    @Override
    public int numOutputs() { return 0; }

    @Override
    public void mix(FloatBuffer[] ins, FloatBuffer out) {
        throw new UnsupportedOperationException("AllPartsMix is a read-only description of all parts, not a real mix");
    }

}

package org.raincityvoices.ttrack.service.audio.model;

import java.nio.FloatBuffer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@Getter(onMethod=@__(@JsonProperty()))
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

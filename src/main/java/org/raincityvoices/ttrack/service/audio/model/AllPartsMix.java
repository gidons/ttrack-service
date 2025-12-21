package org.raincityvoices.ttrack.service.audio.model;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

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

    int numParts;

    @JsonCreator
    public AllPartsMix(int numParts) {
        this.numParts = numParts;
    }

    @Override
    public int numInputs() { return numParts; }

    @Override
    public int numOutputs() { return numParts; }

    @Override
    public void mix(FloatBuffer[] ins, FloatBuffer out) {
        int leastRemaining = Stream.of(ins).mapToInt(FloatBuffer::remaining).min().getAsInt();
        while (leastRemaining-- > 0) {
            for (int i = 0; i < numInputs(); ++i) {
                out.put(ins[i].get());
            }
        }                
    }

}

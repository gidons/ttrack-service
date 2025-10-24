package org.raincityvoices.ttrack.service.audio.model;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Accessors(fluent = true)
@Value
@EqualsAndHashCode(exclude = "sample")
@Jacksonized
public class MonoMix implements AudioMix {
    private static final double TOTAL_FACTOR_TOLERANCE = 0.0001;

    /** 
     * mixFactors[i] is a number between 0.0 and 1.0 that is used to multiply
     * the audio from the ith input part when mixing.
     */
    float[] mixFactors;
    @JsonIgnore
    float[] sample;

    @JsonCreator
    public MonoMix(float... mixFactors) {
        this.mixFactors = mixFactors.clone();
        this.sample = new float[numInputs()];
        validate();
    }

    @Override
    public int numInputs() {
        return mixFactors.length;
    }

    @Override
    public int numOutputs() {
        return 1;
    }

    public void mixOne(FloatBuffer in, FloatBuffer out) {
        float mixed = 0.0f;
        for (int i = 0; i < numInputs(); i++) {
            mixed += in.get() * mixFactors[i];
        }
        out.put(mixed);
    }

    public void mix(FloatBuffer[] ins, FloatBuffer out) {
        int leastRemaining = Stream.of(ins).mapToInt(FloatBuffer::remaining).min().getAsInt();
        while (leastRemaining-- > 0) {
            mixOne(ins, out);
        }
    }

    public void mixOne(FloatBuffer[] ins, FloatBuffer out) {
        float mixed = 0.0f;
        for (int i = 0; i < numInputs(); i++) {
            mixed += ins[i].get() * mixFactors[i];
        }
        out.put(mixed);
    }

    public float mixOne(float[] ins) {
        float mixed = 0.0f;
        for (int i = 0; i < numInputs(); i++) {
            mixed += ins[i] * mixFactors[i];
        }
        return mixed;
    }

    public void validate() {
        if (numInputs() == 0) {
            throw new IllegalStateException("No inputs to mix");
        }
        double sum = 0.0;
        for (int i = 0; i < mixFactors.length; i++) {
            double f = mixFactors[i];
            if (f < 0.0 || f > 1.0) {
                throw new IllegalStateException("Mix factor out of range [0.0, 1.0]: " + f);
            }
            sum += f;
        }
        if (Math.abs(sum - 1.0) > TOTAL_FACTOR_TOLERANCE) {
            throw new IllegalStateException("Mix factors add up to " + sum + ", expected 1.0");
        }
    }

}

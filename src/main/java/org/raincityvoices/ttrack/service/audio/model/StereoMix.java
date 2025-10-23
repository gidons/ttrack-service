package org.raincityvoices.ttrack.service.audio.model;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

/**
 * Description of a mix of one or more audio channels with per-channel volume settings.
 * This can easily be generalized to more output channels, but for now we only need stereo.
 */
@Value
@Accessors(fluent = true)
@Jacksonized
@JsonIgnoreProperties({"sample", "leftMix", "rightMix"})
public class StereoMix implements AudioMix {

    private static final double TOTAL_FACTOR_TOLERANCE = 0.0001;
    final static int NUM_CHANNELS = 2;
    final static int LEFT_CHANNEL = 0;
    final static int RIGHT_CHANNEL = 1;

    /** Mix factors for each channel. */
    float[] leftFactors;
    float[] rightFactors;

    MonoMix leftMix;
    MonoMix rightMix;
    float[] sample;

    @JsonCreator
    public StereoMix(@JsonProperty("leftFactors") float[] leftMixFactors, 
                     @JsonProperty("rightFactors") float[] rightMixFactors) {
        Preconditions.checkNotNull(leftMixFactors);
        Preconditions.checkNotNull(rightMixFactors);
        Preconditions.checkArgument(leftMixFactors.length == rightMixFactors.length);
        this.leftFactors = leftMixFactors;
        this.rightFactors = rightMixFactors;
        this.leftMix = new MonoMix(leftMixFactors);
        this.rightMix = new MonoMix(rightMixFactors);
        this.sample = new float[numInputs()];
    }

    @Override
    public int numInputs() {
        return leftFactors.length;
    }

    @Override
    public int numOutputs() {
        return NUM_CHANNELS;
    }

    @Override
    public void mix(FloatBuffer[] ins, FloatBuffer out) {
        int leastRemaining = Stream.of(ins).mapToInt(FloatBuffer::remaining).min().getAsInt();
        while (leastRemaining-- > 0) {
            for (int i = 0; i < numInputs(); ++i) {
                sample[i] = ins[i].get();
            }
            out.put(leftMix.mixOne(sample));
            out.put(rightMix.mixOne(sample));
        }
    }
}

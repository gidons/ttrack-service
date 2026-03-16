package org.raincityvoices.ttrack.service.audio.model;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

/**
 * An {@link AudioMix} that mixes the input parts according to specific weights
 * to a stereo (two-channel) output. The input weights for each channel must be
 * non-negative and sum to 1.
 * <p>
 * For example, {@code StereoMix({0.25, 0.25, 0.25, 0.25}, {1.0, 0.0, 0.0, 0.0})}
 * will produce an equally-mixed left channel, and the first input part as-is in
 * the right channel.
 * <p>
 * Note: this class is _not_ thread-safe.
 */
@Value
@ToString(exclude = {"leftMix", "rightMix", "sample"})
@Accessors(fluent = true)
@Jacksonized
@JsonIgnoreProperties({"sample", "leftMix", "rightMix"})
public class StereoMix implements AudioMix {

    private static final double TOTAL_FACTOR_TOLERANCE = 0.0001;
    final static int NUM_CHANNELS = 2;
    final static int LEFT_CHANNEL = 0;
    final static int RIGHT_CHANNEL = 1;

    /** Mix factors for each channel. */
    @JsonProperty("leftFactors")
    float[] leftFactors;
    @JsonProperty("rightFactors")
    float[] rightFactors;

    MonoMix leftMix;
    MonoMix rightMix;
    // Pre-allocated array for doing the mixing in, to avoid reallocating for every sample.
    float[] sample;

    @JsonCreator
    public StereoMix(float[] leftFactors, float[] rightFactors) {
        Preconditions.checkNotNull(leftFactors);
        Preconditions.checkNotNull(rightFactors);
        Preconditions.checkArgument(leftFactors.length == rightFactors.length);
        this.leftFactors = leftFactors;
        this.rightFactors = rightFactors;
        this.leftMix = new MonoMix(leftFactors);
        this.rightMix = new MonoMix(rightFactors);
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

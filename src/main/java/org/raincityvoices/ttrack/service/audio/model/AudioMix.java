package org.raincityvoices.ttrack.service.audio.model;

import java.beans.Transient;
import java.nio.FloatBuffer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A representation of a specific way to mix specific audio parts into one or more output channels.
 */
// TODO Add parameters for pitch shift and time stretch.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MonoMix.class, name = "MonoMix"),
    @JsonSubTypes.Type(value = StereoMix.class, name = "StereoMix"),
    @JsonSubTypes.Type(value = AllPartsMix.class, name = "AllPartsMix")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface AudioMix {

    /** The number of input parts. */
    int numInputs();

    /** Whether there is only a single input. */
    @Transient
    default boolean isSingleInput() { return numInputs() == 1; }

    /** The number of output channels. */
    int numOutputs();

    void mix(FloatBuffer[] ins, FloatBuffer out);
}
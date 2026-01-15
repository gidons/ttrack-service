package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

/** 
 * Base class for all types of request that can be posted to {@link SongController#createMixTracks}. 
 */
@JsonTypeInfo(use = Id.DEDUCTION)
@JsonSubTypes({
    @Type(CreateMixTrackRequest.class),
    @Type(CreateMixTrackPackageRequest.class)
})
public abstract class CreateMixRequestBase {
    public static final Range<Double> SPEED_FACTOR_VALID_RANGE = Range.of(0.1, 3.0);
    public static final Range<Integer> PITCH_SHIFT_VALID_RANGE = Range.of(-11, 11); // up to almost one octave each way

    public abstract List<AudioPart> parts();
    public abstract Integer pitchShift();
    public abstract Double speedFactor();
    public String validate() {
        if (CollectionUtils.isEmpty(parts())) {
            return "Missing or empty required parts list.";
        }
        if (!SPEED_FACTOR_VALID_RANGE.contains(speedFactor())) {
            return "Invalid speed factor: " + speedFactor();
        }
        if (!PITCH_SHIFT_VALID_RANGE.contains(pitchShift())) {
            return "Invalid pitch shift: " + pitchShift();
        }
        return null;
    }
}

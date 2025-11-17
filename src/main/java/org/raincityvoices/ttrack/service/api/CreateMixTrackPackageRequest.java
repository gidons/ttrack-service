package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.raincityvoices.ttrack.service.audio.MixUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = false)
@Accessors(fluent = true)
@Builder
@Jacksonized
public class CreateMixTrackPackageRequest extends CreateMixRequestBase {
    List<AudioPart> parts;
    /** String descriptions of the stereo mix, parseable by {@link MixUtils#parseStereoMix(String, List)}. */
    List<String> mixDescriptions;
    @Default Integer pitchShift = 0;
    @Default Double speedFactor = 1.0;
    /** String suffix to attach to mix descriptions, e.g. "slowed down", "in D", or "v2". */
    String packageDescription;
    @Override 
    public String validate() {
        if (CollectionUtils.isEmpty(mixDescriptions())) {
            return "Missing required mix descriptions.";
        }
        return super.validate();
    }
}

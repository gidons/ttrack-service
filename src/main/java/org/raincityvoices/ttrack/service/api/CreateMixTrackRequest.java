package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import lombok.Builder;
import lombok.Value;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = false)
@Accessors(fluent = true)
@Builder
@Jacksonized
public class CreateMixTrackRequest extends CreateMixRequestBase {
    String name;
    List<AudioPart> parts;
    String description;
    @Default Integer pitchShift = 0;
    @Default Double speedFactor = 1.0;

    public String description() {
        return StringUtils.defaultIfBlank(description, name);
    }
}

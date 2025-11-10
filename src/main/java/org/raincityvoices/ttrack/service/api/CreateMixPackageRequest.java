package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import lombok.Builder;
import lombok.Value;
import lombok.Builder.Default;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@Accessors(fluent = true)
@Builder
@Jacksonized
public class CreateMixPackageRequest {
    List<AudioPart> parts;
    List<String> mixTypes;
    int pitchShift;
    @Default double speedFactor = 1.0;
}

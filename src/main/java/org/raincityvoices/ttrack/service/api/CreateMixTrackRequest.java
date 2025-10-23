package org.raincityvoices.ttrack.service.api;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Value
@Accessors(fluent = true)
@Builder
@Jacksonized
public class CreateMixTrackRequest {
    String name;
    List<AudioPart> parts;
    String description;

    public String description() {
        return StringUtils.defaultIfBlank(description, name);
    }
}

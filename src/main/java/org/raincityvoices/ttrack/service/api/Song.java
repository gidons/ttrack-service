package org.raincityvoices.ttrack.service.api;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Song {
    @Default SongId id = SongId.NONE;
    String title;
    String version;
    String arranger;
    String key;
    int durationSec;
}

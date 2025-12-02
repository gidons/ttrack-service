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
    /** Full title of the song. Recommended to be unique. */
    String title;
    /** A short title that is used as prefix to track names. */
    String shortTitle;
    /** TBA. */
    String version;
    String arranger;
    String key;
    int durationSec;
}

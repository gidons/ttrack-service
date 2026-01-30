package org.raincityvoices.ttrack.service.api;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class AppSong {
    Song song;
    List<String> parts;
    String allChannelMediaUrl;
    Instant mediaUpdated;
    String textDataUrl;
    Instant textDataUpdated;
}

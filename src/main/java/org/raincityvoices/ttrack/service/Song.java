package org.raincityvoices.ttrack.service;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Song {
    String id;
    String title;
    String arranger;
    String key;
    int durationSec;
}

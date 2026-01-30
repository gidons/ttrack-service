package org.raincityvoices.ttrack.service.storage;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Value;

public interface TimedDataStorage {

    @Value
    @Builder
    public class TimedDataMetadata {
        String type;
        Instant created;
        Instant updated;
    }

    List<TimedDataMetadata> listDataForSong(String songId);
    List<TimedTextDTO> getAllDataForSong(String songId);
    void putDataForSong(String songId, TimedTextDTO data);
}

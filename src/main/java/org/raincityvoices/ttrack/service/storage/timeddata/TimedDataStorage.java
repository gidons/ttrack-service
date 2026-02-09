package org.raincityvoices.ttrack.service.storage.timeddata;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Value;

public interface TimedDataStorage {

    @Value
    @Builder
    public class TimedDataMetadata {
        String type;
        String part;
        Instant created;
        Instant updated;
    }

    List<TimedDataMetadata> listDataForSong(String songId);
    List<TimedTextDTO> getAllDataForSong(String songId);
    void putDataForSong(String songId, TimedTextDTO data);
    List<TimedTextDTO> getAllDataForPart(String songId, String part);
    TimedTextDTO getDataForPart(String songId, String part, String type);
}

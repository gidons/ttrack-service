package org.raincityvoices.ttrack.service.storage;

import java.util.List;

public interface TimedDataStorage {
    List<TimedTextDTO> getAllDataForSong(String songId);
    void putDataForSong(String songId, TimedTextDTO data);
}

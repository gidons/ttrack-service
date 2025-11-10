package org.raincityvoices.ttrack.service.storage;

import org.raincityvoices.ttrack.service.FileMetadata;
import org.raincityvoices.ttrack.service.MediaContent;
import org.raincityvoices.ttrack.service.api.SongId;

public interface MediaStorage {

    MediaContent getMedia(String mediaLocation);
    void putMedia(String mediaLocation, MediaContent content);
    FileMetadata getMediaMetadata(String mediaLocation);
    default String mediaLocationFor(SongId songId, String trackId) {
        return String.format("%s/%s", songId.value(), trackId);
    }
}
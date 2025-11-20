package org.raincityvoices.ttrack.service.storage;

import org.raincityvoices.ttrack.service.api.SongId;

public interface MediaStorage {

    boolean exists(String mediaLocation);
    MediaContent getMedia(String mediaLocation);
    void putMedia(String mediaLocation, MediaContent content);
    FileMetadata getMediaMetadata(String mediaLocation);
    /** 
     * Delete the media at the given location, if it exists. 
     * @return true if the media existed and was deleted, false if it didn't exist.
     */
    boolean deleteMedia(String mediaLocation);
    default String locationFor(SongId songId, String trackId) {
        return String.format("%s/%s", songId.value(), trackId);
    }
}
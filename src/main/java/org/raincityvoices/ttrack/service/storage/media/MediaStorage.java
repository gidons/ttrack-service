package org.raincityvoices.ttrack.service.storage.media;

import java.time.Duration;

import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.storage.files.FileMetadata;
import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;

/**
 * A storage mechanism for files with media type and other metadata.
 */
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
    String getDownloadUrl(String mediaLocation, Duration timeout);
    default String locationFor(AudioTrackDTO trackDto) {
        return locationFor(trackDto.getSongId(), trackDto.getId());
    }
    default String locationFor(SongId songId, String fileId) {
        return locationFor(songId.value(), fileId);
    }
    default String locationFor(String songId, String fileId) {
        return String.format("%s/%s", songId, fileId);
    }
}
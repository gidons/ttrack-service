package org.raincityvoices.ttrack.service.storage.files;

import java.io.File;
import java.time.Duration;

public interface RemoteFileStorage {
    boolean exists(String location);
    /** 
     * Download the file from the given location to the destination, if it has a new etag.
     * @return the metadata from the remote storage, or null if the file doesn't exist.
     * TODO replace null return value with specific exception?
     */
    FileMetadata download(String location, FileMetadata currentMetadata, File destination);
    FileMetadata fetchMetadata(String location);
    String getDownloadUrl(String location, Duration timeout);
    void upload(File source, String location);
    void updateMetadata(FileMetadata metadata, String location);
    void delete(String mediaLocation);
}
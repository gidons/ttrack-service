package org.raincityvoices.ttrack.service.storage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.raincityvoices.ttrack.service.FileMetadata;
import org.springframework.stereotype.Component;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A MediaStorage implementation that uses Azure Blob Storage as the backend,
 * and caches files - both media and its metadata - locally on disk.
 * 
 * Caching happens both ways:
 * - On write (putMedia), the media stream is written as a file in the cache. Some metadata
 *   is then inferred (duration, content type) and written to disk as well. Finally, the
 *   media is uploaded to Blob storage, and the metadata is stored as Blob properties.
 * - On read (getMedia), the Blob's ETag is compared with the cached metadata, and if it's
 *   different, the stream is downloaded to the cache, then the metadata is updated as well.
 * 
 * Some notes and pitfalls:
 * - This class assumes that <em>all reads and writes flow through it</em>. This is important
 *   especially because if there are out-of-band writes, the metadata might be incorrect or
 *   incomplete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlobMediaClient implements DiskCachingMediaStorage.RemoteStorage {

    private final BlobContainerClient containerClient;

    @Override
    public void downloadMedia(String location, File destination) {
        log.info("Downloading blob {} to local file {}", location, destination.getAbsolutePath());
        client(location).downloadToFile(destination.getAbsolutePath(), true);   
    }
    
    @Override
    public void uploadMedia(File source, String location) {        
        log.info("Uploading from {} to {}...", source.getAbsolutePath(), location);
        try {
            client(location).upload(new BufferedInputStream(new FileInputStream(source)), true);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to upload media to blob " + location, e);
        }
    }

    @Override
    public boolean exists(String location) {
        return client(location).exists();
    }

    @Override
    public FileMetadata fetchMetadata(String location) {
        log.info("Fetching   metadata for {}", location);
        return FileMetadata.fromBlobProperties(client(location).getProperties());
    }

    @Override
    public void updateMetadata(FileMetadata metadata, String location) {
        log.info("Updating metadata for {}", location);
        log.debug("New metadata: {}", metadata);
        client(location).setHttpHeaders(metadata.toBlobHttpHeaders());
    }

    public void delete(String location) {
        log.info("Deleting {}", location);
        client(location).deleteIfExists();
    }

    private BlobClient client(String location) { return containerClient.getBlobClient(location); }
}

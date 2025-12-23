package org.raincityvoices.ttrack.service.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.springframework.stereotype.Component;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;

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

    private final BlobContainerClient mediaContainerClient;

    @Override
    public FileMetadata downloadMedia(String location, FileMetadata currentMetadata, File destination) {
        String currentETag = currentMetadata.etag();
        log.info("Downloading blob {} to local file {} (current ETag: {})", location, destination.getAbsolutePath(), currentETag);
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(destination))) {
            BlobDownloadResponse response = client(location).downloadStreamWithResponse(stream, null, null, 
                new BlobRequestConditions().setIfNoneMatch(currentETag), false, null, null);
            return FileMetadata.fromBlobDownloadHeaders(response.getDeserializedHeaders());
        } catch(BlobStorageException e) {
            switch (e.getStatusCode()) {
                case 304: 
                    log.info("ETag hasn't changed.");
                    return fetchMetadata(location);
                case 404:
                    log.info("Blob does not exist.");
                    return null;
                default:
                    throw new RuntimeException("Failed to download media from blob " + location + " to local file " + destination);                    
            }
        } catch(Exception e) {
            throw new RuntimeException("Failed to download media from blob " + location + " to local file " + destination);
        }
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
        log.info("Fetching metadata for {}", location);
        try {
            return FileMetadata.fromBlobProperties(client(location).getProperties());
        } catch(BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                log.info("No blob named {} found", location);
                return null;
            }
            throw e;
        }
    }

    @Override
    public void updateMetadata(FileMetadata metadata, String location) {
        log.info("Updating metadata for {}", location);
        log.debug("New metadata: {}", metadata);
        client(location).setHttpHeaders(metadata.toBlobHttpHeaders());
    }

    public void deleteMedia(String location) {
        log.info("Deleting {}", location);
        client(location).deleteIfExists();
    }

    private BlobClient client(String location) { return mediaContainerClient.getBlobClient(location); }
}

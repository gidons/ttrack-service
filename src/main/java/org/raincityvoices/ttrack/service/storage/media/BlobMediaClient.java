package org.raincityvoices.ttrack.service.storage.media;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.Duration;

import org.raincityvoices.ttrack.service.storage.files.DownloadUrlHelper;
import org.raincityvoices.ttrack.service.storage.files.FileMetadata;
import org.raincityvoices.ttrack.service.storage.files.RemoteFileStorage;
import org.springframework.stereotype.Component;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A MediaStorage implementation that persists to Azure Blob Storage.
 * Media metadata is stored as blob properties.
 * Note that the term "media" here is used in its general HTTP sense of content,
 * and not limited to audio/video/etc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlobMediaClient implements RemoteFileStorage {

    private final BlobContainerClient mediaContainerClient;
    private final DownloadUrlHelper downloadUrlHelper;

    @Override
    public FileMetadata download(String location, FileMetadata currentMetadata, File destination) {
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
    public String getDownloadUrl(String location, Duration timeout) {
        return downloadUrlHelper.getDownloadUrl(client(location), timeout);
    }
    
    @Override
    public void upload(File source, String location) {        
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
            FileMetadata metadata = FileMetadata.fromBlobProperties(client(location).getProperties());
            log.debug("Metadata fetched: {}", metadata);
            return metadata;
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

    public void delete(String location) {
        log.info("Deleting {}", location);
        client(location).deleteIfExists();
    }

    private BlobClient client(String location) { return mediaContainerClient.getBlobClient(location); }
}

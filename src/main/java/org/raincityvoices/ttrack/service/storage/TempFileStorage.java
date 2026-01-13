package org.raincityvoices.ttrack.service.storage;

import java.io.File;
import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.google.common.annotations.VisibleForTesting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TempFileStorage {

    private final BlobContainerClient tempFileContainerClient;
    private final DownloadUrlHelper downloadUrlHelper;

    @VisibleForTesting Clock clock = Clock.systemUTC();

    public void createFile(String location, FileMetadata metadata, File localFile) {
        log.info("Uploading from {} to {}/{}", localFile, tempFileContainerClient.getBlobContainerName(), location);
        BlobClient client = client(location);
        client.uploadFromFile(localFile.getAbsolutePath());
        client.setHttpHeaders(metadata.toBlobHttpHeaders());
    }

    public String getDownloadUrl(String location, Duration timeout) {
        BlobClient client = client(location);
        return downloadUrlHelper.getDownloadUrl(client, timeout);
    }

    private BlobClient client(String location) {
        return tempFileContainerClient.getBlobClient(location);
    }
}

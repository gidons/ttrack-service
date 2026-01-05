package org.raincityvoices.ttrack.service.storage;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;
import com.google.common.annotations.VisibleForTesting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TempFileStorage {

    private final BlobContainerClient tempFileContainerClient;
    private final BlobServiceClient blobServiceClient;

    @VisibleForTesting Clock clock = Clock.systemUTC();

    public void createFile(String location, FileMetadata metadata, File localFile) {
        log.info("Uploading from {} to {}/{}", localFile, tempFileContainerClient.getBlobContainerName(), location);
        BlobClient client = client(location);
        client.uploadFromFile(localFile.getAbsolutePath());
        client.setHttpHeaders(metadata.toBlobHttpHeaders());
    }

    public String getDownloadUrl(String location, Duration timeout) {
        BlobClient client = client(location);
        OffsetDateTime expiry = clock.instant().plus(timeout).atOffset(ZoneOffset.UTC);

        // Create a User Delegation Key so we can sign a SAS using Azure AD credentials
        OffsetDateTime keyStart = clock.instant().atOffset(ZoneOffset.UTC).minusMinutes(5);
        OffsetDateTime keyExpiry = keyStart.plus(timeout).plusMinutes(5);
        UserDelegationKey userDelegationKey = blobServiceClient.getUserDelegationKey(keyStart, keyExpiry);

        // Build BlobServiceSasSignatureValues with permissions for the single blob
        BlobSasPermission permission = BlobSasPermission.parse("r");
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiry, permission)
            .setStartTime(keyStart)
            .setProtocol(SasProtocol.HTTPS_ONLY);

        String sasToken = client.generateUserDelegationSas(sasValues, userDelegationKey);
        String url = client.getBlobUrl() + "?" + sasToken;
        log.info("Download URL for {}: {}", location, url);
        return url;
    }

    private BlobClient client(String location) {
        return tempFileContainerClient.getBlobClient(location);
    }
}

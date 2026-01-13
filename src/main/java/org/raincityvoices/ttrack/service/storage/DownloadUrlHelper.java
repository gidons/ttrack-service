package org.raincityvoices.ttrack.service.storage;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.azure.storage.blob.BlobClient;
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
public class DownloadUrlHelper {
    private final BlobServiceClient blobServiceClient;

    @VisibleForTesting Clock clock = Clock.systemUTC();

    public String getDownloadUrl(BlobClient client, Duration timeout) {
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
        log.info("Download URL for {}/{}: {}", client.getContainerName(), client.getBlobName(), url);
        return url;
    }

}

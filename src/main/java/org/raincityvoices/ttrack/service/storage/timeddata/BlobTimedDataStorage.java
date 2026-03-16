package org.raincityvoices.ttrack.service.storage.timeddata;

import java.util.List;

import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.stereotype.Component;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlobTimedDataStorage implements TimedDataStorage {

    private final BlobContainerClient dataContainerClient;

    @Override
    public List<TimedDataMetadata> listDataForSong(String songId) {
        log.info("Listing all timed data for song {}", songId);
        PagedIterable<BlobItem> items = dataContainerClient.listBlobs(new ListBlobsOptions().setPrefix(songId), null);
        return items.stream()
            .peek(item -> log.info("Item: '{}' ({})", item.getName(), item.isPrefix()))
            .filter(item -> !item.isPrefix())
            .map(this::fromBlobItem)
            .toList();
    }

    private TimedDataMetadata fromBlobItem(BlobItem item) {
        String[] elements = item.getName().split("/");
        if (elements.length != 3) {
            log.error("Invalid item name: '{}'", item.getName());
            return null;
        }
        TimedDataMetadata md = TimedDataMetadata.builder()
            .part(elements[1])
            .type(elements[2].replace(".json", ""))
            .created(item.getProperties().getCreationTime().toInstant())
            .updated(item.getProperties().getLastModified().toInstant())
            .build();
        log.info("Returning timed data metadata: {}", md);
        return md;
    }

    @Override
    public List<TimedTextDTO> getAllDataForSong(String songId) {
        log.info("Getting all timed data for song {}", songId);
        PagedIterable<BlobItem> items = dataContainerClient.listBlobs(new ListBlobsOptions().setPrefix(songId), null);
        return items.stream()
            .peek(item -> log.debug("Item: '{}' ({})", item.getName(), item.isPrefix()))
            .filter(item -> !item.isPrefix())
            .map(item -> readOneBlob(songId, item.getName()))
            .toList();
    }

    @Override
    public List<TimedTextDTO> getAllDataForPart(String songId, String part) {
        log.info("Getting all timed data for part {}/{}", songId, part);
        PagedIterable<BlobItem> items = dataContainerClient.listBlobs(new ListBlobsOptions().setPrefix(songId + "/" + part), null);
        return items.stream()
            .peek(item -> log.debug("Item: '{}' ({})", item.getName(), item.isPrefix()))
            .filter(item -> !item.isPrefix())
            .map(item -> readOneBlob(songId, item.getName()))
            .toList();
    }

    @Override
    public TimedTextDTO getDataForPart(String songId, String part, String type) {
        try {
            return readOneBlob(songId, blobName(songId, part, type));
        } catch(BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public void putDataForSong(String songId, TimedTextDTO data) {
        log.info("Storing timed data for song {}, part {}, and type {} ({} entries)", songId, data.part(), data.type(), data.entries().size());
        String content = JsonUtils.toJson(data);
        dataContainerClient.getBlobClient(blobName(songId, data.part(), data.type())).upload(BinaryData.fromString(content), true);
    }

    public void deleteData(String songId, String part, String type) {
        log.info("Deleting data for song {}, part {}, and type {}", songId, part, type);
        dataContainerClient.getBlobClient(blobName(songId, part, type)).deleteIfExists();
    }

    private String blobName(String songId, String part, String type) {
        return songId + "/" + part + "/" + type + ".json";
    }

    private TimedTextDTO readOneBlob(String songId, String itemName) {
        log.info("Reading timed data from blob {}", itemName);
        String content = dataContainerClient.getBlobClient(itemName).downloadContent().toString();
        try {
            TimedTextDTO dto = JsonUtils.fromJson(content, TimedTextDTO.class);
            log.info("Read {} entries of type {}", dto.entries().size(), dto.type());
            if (!itemName.equals(blobName(songId, dto.part(), dto.type()))) {
                log.warn("Data blob has unexpected name: type={}, name={}", dto.type(), itemName);
            }
            return dto;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse contents of blob " + itemName, e);
        }
    }

}

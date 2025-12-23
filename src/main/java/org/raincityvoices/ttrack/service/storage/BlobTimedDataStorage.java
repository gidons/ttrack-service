package org.raincityvoices.ttrack.service.storage;

import java.util.List;

import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.stereotype.Component;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlobTimedDataStorage implements TimedDataStorage {

    private final BlobContainerClient dataContainerClient;

    @Override
    public List<TimedTextDTO> getAllDataForSong(String songId) {
        log.info("Getting all timed data for song {}", songId);
        PagedIterable<BlobItem> items = dataContainerClient.listBlobsByHierarchy(songId + "/");
        return items.stream()
            .filter(item -> !item.isPrefix())
            .map(item -> readOneBlob(songId, item))
            .toList();
    }

    @Override
    public void putDataForSong(String songId, TimedTextDTO data) {
        log.info("Storing timed data for song {} and type {} ({} entries)", songId, data.type(), data.entries().size());
        String content = JsonUtils.toJson(data);
        dataContainerClient.getBlobClient(blobName(songId, data.type())).upload(BinaryData.fromString(content), true);
    }

    public void deleteData(String songId, String type) {
        log.info("Deleting data for song {} and type {}", songId, type);
        dataContainerClient.getBlobClient(blobName(songId, type)).deleteIfExists();
    }

    private String blobName(String songId, String type) {
        return songId + "/" + type + ".json";
    }

    private TimedTextDTO readOneBlob(String songId, BlobItem item) {
        log.info("Reading timed data from blob {}", item.getName());
        String content = dataContainerClient.getBlobClient(item.getName()).downloadContent().toString();
        try {
            TimedTextDTO dto = JsonUtils.fromJson(content, TimedTextDTO.class);
            log.info("Read {} entries of type {}", dto.entries().size(), dto.type());
            if (!item.getName().equals(blobName(songId, dto.type()))) {
                log.warn("Data blob has unexpected name: type={}, name={}", dto.type(), item.getName());
            }
            return dto;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse contents of blob " + item.getName(), e);
        }
    }

}

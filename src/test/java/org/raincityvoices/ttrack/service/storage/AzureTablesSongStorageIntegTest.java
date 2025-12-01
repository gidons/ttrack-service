package org.raincityvoices.ttrack.service.storage;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.exceptions.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import com.azure.data.tables.TableClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class AzureTablesSongStorageIntegTest {

    private static final String SONG_ID = "12345678";
    private static final String SONG_TITLE = "The Test Song";

    @Autowired
    private AzureTablesSongStorage storage;
    @Autowired
    @Qualifier("songsTableClient")
    private TableClient tableClient;

    @AfterEach
    public void cleanup() {
        try {
            log.info("Cleaning up song entity if necessary...");
            tableClient.deleteEntity(SONG_ID, "");
        } catch(IllegalArgumentException e) {
            log.info("No entity to delete.");
        }
    }

    @Test
    public void GIVEN_song_exists_WHEN_writeSong_with_no_etag_THEN_throws_ConflictException() {

        log.info("Creating song entity...");
        SongDTO dto = SongDTO.builder()
            .id(SONG_ID)
            .title(SONG_TITLE)
            .key("D")
            .durationSec(123)
            .arranger("Jane Doe")
            .build();
        storage.writeSong(dto);
        log.info("ETag: {}", dto.getETag());

        log.info("Trying to create again with no ETag");
        dto.setETag(null);
        assertThrows(ConflictException.class, () -> storage.writeSong(dto));
    }

    @Test
    public void GIVEN_song_exists_WHEN_writeSong_with_same_etag_THEN_succeeds_and_etag_is_updated() {

        log.info("Creating song entity...");
        SongDTO dto = SongDTO.builder()
            .id(SONG_ID)
            .title(SONG_TITLE)
            .key("D")
            .durationSec(123)
            .arranger("Jane Doe")
            .build();
        storage.writeSong(dto);
        String oldEtag = dto.getETag().toString();
        log.info("ETag: {}", oldEtag);

        dto.setDurationSec(321);
        log.info("Updating with same ETag");
        storage.writeSong(dto);

        assertNotEquals(oldEtag, dto.getETag().toString());
    }
}

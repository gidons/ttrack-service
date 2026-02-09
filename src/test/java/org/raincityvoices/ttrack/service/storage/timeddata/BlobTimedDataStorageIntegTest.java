package org.raincityvoices.ttrack.service.storage.timeddata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.timeddata.TimedTextDTO.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.azure.cosmos.implementation.guava25.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class BlobTimedDataStorageIntegTest {

    private static final String DATA_TYPE_1 = "TEST_1";
    private static final String DATA_TYPE_2 = "TEST_2";
    private static final String SONG_ID = TestData.TEST_SONG_ID;

    @Autowired
    private BlobTimedDataStorage storage;

    @BeforeEach
    public void before() { cleanup(); }

    @AfterEach
    public void after() { cleanup(); }

    @Test
    public void testEndToEnd() {
        List<TimedTextDTO> existing = storage.getAllDataForSong(SONG_ID);
        assertEquals(List.of(), existing);
        TimedTextDTO dto1 = TimedTextDTO.builder()
            .type(DATA_TYPE_1)
            .part("Bass")
            .entries(ImmutableList.of(
                new Entry(123, "foo"),
                new Entry(234, "bar")
            ))
            .build();
        TimedTextDTO dto2 = TimedTextDTO.builder()
            .type(DATA_TYPE_2)
            .part("Bari")
            .entries(ImmutableList.of(
                new Entry(345, "FOO"),
                new Entry(456, "BAR")
            ))
            .build();
        storage.putDataForSong(SONG_ID, dto1);
        storage.putDataForSong(SONG_ID, dto2);

        List<TimedTextDTO> actual = storage.getAllDataForSong(SONG_ID);
        assertEquals(Set.of(dto1, dto2), ImmutableSet.copyOf(actual));
    }

    private void cleanup() {
        storage.deleteData(SONG_ID, "Bass", DATA_TYPE_1);
        storage.deleteData(SONG_ID, "Bari", DATA_TYPE_2);
    }
}

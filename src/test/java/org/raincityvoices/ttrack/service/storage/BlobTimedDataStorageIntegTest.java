package org.raincityvoices.ttrack.service.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.TimedTextDTO.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.azure.cosmos.implementation.guava25.collect.ImmutableList;

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
            .parts(new String[] { "Bass", "Lead" })
            .entries(ImmutableList.of(
                Entry.builder().t(123).u("foo").build(),
                Entry.builder().t(234).p("foo","bar").build(),
                Entry.builder().t(345).u("bar").build(),
                Entry.builder().t(456).p("bar", "foo").build()
            ))
            .build();
        TimedTextDTO dto2 = TimedTextDTO.builder()
            .type(DATA_TYPE_2)
            .parts(new String[] { "Bari", "Tenor" })
            .entries(ImmutableList.of(
                Entry.builder().t(123).u("FOO").build(),
                Entry.builder().t(234).p("FOO","BAR").build(),
                Entry.builder().t(345).u("BAR").build(),
                Entry.builder().t(456).p("BAR", "FOO").build()
            ))
            .build();
        storage.putDataForSong(SONG_ID, dto1);
        storage.putDataForSong(SONG_ID, dto2);

        List<TimedTextDTO> actual = storage.getAllDataForSong(SONG_ID);
        assertEquals(List.of(dto1, dto2), actual);
    }

    private void cleanup() {
        storage.deleteData(SONG_ID, DATA_TYPE_1);
        storage.deleteData(SONG_ID, DATA_TYPE_2);
    }
}

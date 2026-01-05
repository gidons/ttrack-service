package org.raincityvoices.ttrack.service.async;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.raincityvoices.ttrack.service.model.TestData.TEST_SONG_ID;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.async.AsyncTaskManager.TaskExec;
import org.raincityvoices.ttrack.service.async.AudioTrackTask.Output;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class ProcessUploadedTrackTaskIntegTest {

    private static final String TEST_PART_NAME = "Bass";

    @Autowired
    private AsyncTaskManager taskManager;

    @Autowired
    private SongStorage songStorage;

    @Autowired
    private MediaStorage mediaStorage;

    @Autowired
    private AsyncTaskStorage taskStorage;

    @BeforeEach
    @AfterEach
    public void cleanup() {
        log.info("Cleaning up media for {}...", AudioTrackDTO.fqId(TEST_SONG_ID, TEST_PART_NAME));
        mediaStorage.deleteMedia(mediaStorage.locationFor(new SongId(TEST_SONG_ID), TEST_PART_NAME));
        log.info("Cleaning up metadata for {}...", AudioTrackDTO.fqId(TEST_SONG_ID, TEST_PART_NAME));
        songStorage.deleteTrack(TEST_SONG_ID, TEST_PART_NAME);
        songStorage.deleteSong(TEST_SONG_ID);
    }

    @Test
    public void test() throws InterruptedException, ExecutionException {
        songStorage.writeSong(SongDTO.builder()
            .id(TEST_SONG_ID)
            .title("Test Song")
            .shortTitle("Test")
            .arranger("John Doe")
            .build());
        // Copy the part from Sunshine and upload it to the test song
        MediaContent media = mediaStorage.getMedia(mediaStorage.locationFor(TestData.SUNSHINE_SONG_ID, TEST_PART_NAME));
        String targetLocation = mediaStorage.locationFor(TEST_SONG_ID, TEST_PART_NAME);
        mediaStorage.putMedia(targetLocation, media);
        AudioTrackDTO track = songStorage.writeTrack(AudioTrackDTO.builder()
            .songId(TEST_SONG_ID)
            .id(TEST_PART_NAME)
            .mediaLocation(targetLocation)
            .build());
        
        Instant preTask = Instant.now();
        TaskExec<ProcessUploadedPartTask, Output> exec = taskManager.schedule(ProcessUploadedPartTask.class, track);
        assertEquals(TEST_SONG_ID, exec.task().songId());
        assertEquals(track.getId(), exec.task().trackId());
        Output output = exec.result().get();
        assertNotNull(output);
        AsyncTaskDTO taskDto = taskStorage.getTask(exec.task().taskId());
        assertEquals(AsyncTaskDTO.SUCCEEDED, taskDto.getStatus());
        AudioTrackDTO trackDto = songStorage.describeTrack(TEST_SONG_ID, TEST_PART_NAME);
        assertThat(trackDto.getUpdated(), greaterThan(preTask));
    }

}

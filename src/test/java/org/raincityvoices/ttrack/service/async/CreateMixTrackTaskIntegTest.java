package org.raincityvoices.ttrack.service.async;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.async.AsyncTaskManager.TaskExec;
import org.raincityvoices.ttrack.service.async.AudioTrackTask.Output;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.audio.model.StereoMix;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class CreateMixTrackTaskIntegTest {

    private static final StereoMix TEST_MIX = new StereoMix(
        new float[] { 0.2f, 0.3f, 0.4f, 0.1f }, 
        new float[] { 0.4f, 0.3f, 0.2f, 0.1f }
    );

    private static final String TEST_MIX_NAME = "test mix";

    @Autowired
    private AsyncTaskManager taskManager;

    @Autowired
    private SongStorage songStorage;

    @Autowired
    private MediaStorage mediaStorage;

    @Autowired
    private AsyncTaskStorage taskStorage;

    @BeforeAll
    public static void init() {
        // Temp.KEEP_FILES = true;
    }

    @AfterEach
    public void cleanup() {
        log.info("Cleaning up media for {}...", AudioTrackDTO.fqId(TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME));
        mediaStorage.deleteMedia(mediaStorage.locationFor(new SongId(TestData.SUNSHINE_SONG_ID), TEST_MIX_NAME));
        log.info("Cleaning up metadata for {}...", AudioTrackDTO.fqId(TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME));
        songStorage.deleteTrack(TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME);
    }

    @Test
    void testProcess() throws Exception {
        AudioTrackDTO mixTrack = AudioTrackDTO.builder()
            .songId(TestData.SUNSHINE_SONG_ID)
            .id(TEST_MIX_NAME)
            .audioMix(TEST_MIX)
            .parts(TestData.BBS_4_PART_NAMES)
            .pitchShift(-1)
            .speedFactor(0.8)
            .build();
        songStorage.writeTrack(mixTrack);
        TaskExec<CreateMixTrackTask, Output> exec = taskManager.schedule(CreateMixTrackTask.class, mixTrack);
        CreateMixTrackTask task = exec.task();
        AsyncTaskDTO taskDto = taskStorage.getTask(task.taskId());
        assertEquals("CreateMixTrack", taskDto.getTaskType());

        Output output = exec.result().get();
        assertNotNull(output.getTrackETag());
        taskDto = taskStorage.getTask(task.taskId());
        assertEquals(AsyncTaskDTO.SUCCEEDED, taskDto.getStatus());
        assertThat(taskDto.getStartTime(), Matchers.lessThan(Instant.now()));
        assertThat(taskDto.getEndTime(), Matchers.lessThan(Instant.now()));
        assertThat(taskDto.getEndTime(), Matchers.greaterThan(taskDto.getStartTime()));

        mixTrack = songStorage.describeMix(TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME);
        AudioTrackDTO partTrack = songStorage.describePart(TestData.SUNSHINE_SONG_ID, TestData.LEAD.name());
        assertTrue(mixTrack.hasMedia());
        assertEquals(partTrack.getDurationSec() * 1 / mixTrack.getSpeedFactor(), mixTrack.getDurationSec(), 1.0);
        FileMetadata metadata = mediaStorage.getMediaMetadata(mediaStorage.locationFor(TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME));
        assertEquals("Sunshine - " + TEST_MIX_NAME + ".mp3", metadata.fileName());
        assertEquals(AudioFormats.MP3_TYPE, metadata.contentType());
        assertEquals(mixTrack.getDurationSec(), metadata.durationSec(), 1.0);
        assertEquals(null, mixTrack.getCurrentTaskId());
    }
}

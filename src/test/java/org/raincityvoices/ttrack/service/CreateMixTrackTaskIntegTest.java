package org.raincityvoices.ttrack.service;

import static org.junit.Assert.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.model.StereoMix;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
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
    private AudioTrackTaskFactory factory;

    @Autowired
    private SongStorage songStorage;

    @Autowired
    private MediaStorage mediaStorage;

    @AfterEach
    public void cleanup() {
        log.info("Cleaning up media for {}/{}...", TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME);
        mediaStorage.deleteMedia(mediaStorage.mediaLocationFor(new SongId(TestData.SUNSHINE_SONG_ID), TEST_MIX_NAME));
        log.info("Cleaning up metadata for {}/{}...", TestData.SUNSHINE_SONG_ID, TEST_MIX_NAME);
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
        CreateMixTrackTask task = factory.newCreateMixTrackTask(mixTrack);
        AudioTrackDTO processed = task.call();
        assertNotNull(processed.getMediaLocation());
    }
}

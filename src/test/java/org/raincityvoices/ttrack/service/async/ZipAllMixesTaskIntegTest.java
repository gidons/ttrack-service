package org.raincityvoices.ttrack.service.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.async.AsyncTaskManager.TaskExec;
import org.raincityvoices.ttrack.service.async.ZipTracksTask.Output;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.storage.TempFileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class ZipAllMixesTaskIntegTest {

    @Autowired
    private AsyncTaskManager taskManager;

    @Autowired
    private TempFileStorage tempStorage;

    @Autowired
    private SongStorage songStorage;

    @Test
    public void test() throws Exception {
        List<String> trackIds = songStorage.listMixesForSong(TestData.SUNSHINE_SONG_ID)
            .stream()
            .filter(t -> !t.getId().equals(SongController.ALL_CHANNEL_MIX_ID))
            .map(t -> t.getId())
            .toList();
        TaskExec<ZipTracksTask, Output> taskExec = taskManager.schedule(ZipTracksTask.class, TestData.SUNSHINE_SONG_ID, trackIds);
        Output output = taskExec.result().get();
        log.info("Task output: {}", output);
        assertEquals("Sunshine.zip", output.getZipFileName());
        String url = tempStorage.getDownloadUrl(output.getDownloadUrl(), Duration.ofMinutes(5));
        log.info("Zip download URL: {}", url);
        assertNotNull(url);
    }
}

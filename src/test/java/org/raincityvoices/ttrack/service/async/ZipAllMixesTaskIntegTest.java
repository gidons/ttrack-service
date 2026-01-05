package org.raincityvoices.ttrack.service.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.async.AsyncTaskManager.TaskExec;
import org.raincityvoices.ttrack.service.async.ZipAllMixesTask.Output;
import org.raincityvoices.ttrack.service.model.TestData;
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

    @Test
    public void test() throws Exception {
        TaskExec<ZipAllMixesTask, Output> taskExec = taskManager.schedule(ZipAllMixesTask.class, TestData.SUNSHINE_SONG_ID);
        Output output = taskExec.result().get();
        log.info("Task output: {}", output);
        // assertEquals(taskExec.task().taskId() + ".zip", output.getDownloadUrl());
        assertEquals("Sunshine.zip", output.getZipFileName());
        String url = tempStorage.getDownloadUrl(output.getDownloadUrl(), Duration.ofMinutes(5));
        log.info("Zip download URL: {}", url);
        assertNotNull(url);
    }
}

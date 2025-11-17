package org.raincityvoices.ttrack.service.audio;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ProcessBuilder.Redirect;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Ffmpeg {

    /** How long to wait for ffmpeg to complete. It typically runs in much less than a second. */
    public static final long FFMPEG_TIMEOUT_SEC = 5;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void convertToMp3(File inputFile, File mp3File) {
        log.info("Running ffmpeg...");
        List<String> command = List.of("ffmpeg", "-y", "-v", "info", "-i", inputFile.getAbsolutePath(), mp3File.getAbsolutePath());
        try {
            log.debug("Command line: '{}'", StringUtils.join(command, " "));
            Process ffmpeg = new ProcessBuilder(command)
                .redirectOutput(Redirect.PIPE)
                .redirectError(Redirect.INHERIT)
                .start();
            executor.submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpeg.getInputStream()));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        log.debug("[PID {}] {}", ffmpeg.pid(), line);
                    }
                } catch (IOException e) {
                    log.error("Exception while reading the stdout of PID {}", ffmpeg.pid());
                }
            });
            log.info("PID: {}", ffmpeg.pid());
            boolean completed = ffmpeg.waitFor(FFMPEG_TIMEOUT_SEC, TimeUnit.SECONDS);
            log.info("ffmpeg completed: {}. Exit code: {}", completed, ffmpeg.exitValue());
            if (!completed) {
                throw new TimeoutException("FFMPEG for file " + inputFile + " not completed within " + FFMPEG_TIMEOUT_SEC + " seconds.");
            }
        } catch(Exception e) {
            throw new RuntimeException("Exception trying to run ffmpeg", e);
        }
    }
}

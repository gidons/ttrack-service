package org.raincityvoices.ttrack.service.tasks;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.Temp;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
@Getter(AccessLevel.PACKAGE)
public class AudioTrackTaskManager {

    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    private final FileManager fileManager;
    private final AsyncTaskStorage asyncTaskStorage;

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    // TODO [SCRUM-27] find a clean and useful way to inject debug settings
    private final AudioDebugger.Settings debugSettings = AudioDebugger.Settings.NONE;

    public Future<AudioTrackDTO> scheduleProcessUploadedTrackTask(AudioTrackDTO track) {
        return schedule(new ProcessUploadedPartTask(track, this));
    }

    public Future<AudioTrackDTO> scheduleUploadPartTrackTask(AudioTrackDTO track, Temp.File audioTempFile, String originalFileName) {
        return schedule(new UploadPartTrackTask(track, audioTempFile, originalFileName, this));
    }

    public Future<AudioTrackDTO> scheduleCreateMixTrackTask(AudioTrackDTO mixTrack) {
        return schedule(new CreateMixTrackTask(mixTrack, this));
    }

    public Future<AudioTrackDTO> scheduleRefreshAllMixesTask(AudioTrackDTO allChannelsTrack) {
        return schedule(new RefreshAllMixesTask(allChannelsTrack, this));
    }

    private <T extends AudioTrackTask> Future<AudioTrackDTO> schedule(T task) {
        try {
            task.initialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize or validate asynchronous task " + task, e);
        }
        return executor.submit(task);
    }
}

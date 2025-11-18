package org.raincityvoices.ttrack.service.tasks;

import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.Ffmpeg;
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
public class AudioTrackTaskFactory {

    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    private final FileManager fileManager;
    private final Ffmpeg ffmpeg;
    // TODO [SCRUM-27] find a clean and useful way to inject debug settings
    private final AudioDebugger.Settings debugSettings = AudioDebugger.Settings.NONE;

    public ProcessUploadedTrackTask newProcessUploadedTrackTask(AudioTrackDTO track) {
        return new ProcessUploadedTrackTask(track, this);
    }

    public UploadPartTrackTask newUploadPartTrackTask(AudioTrackDTO track, Temp.File audioTempFile, String originalFileName) {
        return new UploadPartTrackTask(track, audioTempFile, originalFileName, this);
    }

    public CreateMixTrackTask newCreateMixTrackTask(AudioTrackDTO mixTrack) {
        return new CreateMixTrackTask(mixTrack, this);
    }
}

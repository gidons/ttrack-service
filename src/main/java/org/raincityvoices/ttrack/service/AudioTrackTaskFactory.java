package org.raincityvoices.ttrack.service;

import java.util.List;

import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.TempFile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class AudioTrackTaskFactory {

    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    // TODO find a clean and useful way to inject debug settings
    private final AudioDebugger.Settings debugSettings = AudioDebugger.Settings.NONE;

    public ProcessUploadedTrackTask newProcessUploadedTrackTask(AudioTrackDTO track) {
        return new ProcessUploadedTrackTask(track, songStorage, mediaStorage);
    }

    public UploadPartTrackTask newUploadPartTrackTask(AudioTrackDTO track, TempFile audioTempFile, String originalFileName) {
        return new UploadPartTrackTask(track, audioTempFile, originalFileName, songStorage, mediaStorage);
    }

    public CreateMixTrackTask newCreateMixTrackTask(AudioTrackDTO mixTrack, List<AudioTrackDTO> partTracks) {
        return new CreateMixTrackTask(mixTrack, partTracks, songStorage, mediaStorage, debugSettings);
    }
}

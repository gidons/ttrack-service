package org.raincityvoices.ttrack.service.async;

import java.time.Duration;

import org.raincityvoices.ttrack.service.storage.media.MediaContent;
import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.timeddata.TimedDataStorage;
import org.raincityvoices.ttrack.service.util.PrototypeBean;
import org.springframework.beans.factory.annotation.Autowired;

import com.azure.ai.speech.transcription.TranscriptionClient;
import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents.
 */
@Slf4j
@PrototypeBean
public class ProcessUploadedPartTask extends AudioTrackTask<AudioTrackTask.Input, AudioTrackTask.Output> {

    private static final Duration TRANSCRIPTION_TIMEOUT = Duration.ofMinutes(10);
    private final String mediaLocation;

    @Autowired
    private TranscriptionClient transcriptionClient;
    @Autowired
    private TimedDataStorage timedDataStorage;

    ProcessUploadedPartTask(AudioTrackDTO track) {
        super(new Input(track));
        Preconditions.checkArgument(track.hasMedia());
        Preconditions.checkArgument(track.isPartTrack());
        this.mediaLocation = track.getMediaLocation();
    }

    @Override
    protected String getTaskType() {
        return "ProcessUploadedPart";
    }

    @Override
    public Class<Input> getInputClass() {
        return AudioTrackTask.Input.class;
    }

    @Override
    protected void doInitialize() throws Exception {
        super.doInitialize();
        if (!mediaStorage().exists(mediaLocation)) {
            throw new RuntimeException("No media exists at expected location " + mediaLocation);
        }
    }

    @Override
    protected Output processTrack() throws Exception {
        String location = track().getMediaLocation();
        MediaContent media = mediaStorage().getMedia(location);
        track().updateFileMetadata(media.metadata());
        return new Output();
    }
}

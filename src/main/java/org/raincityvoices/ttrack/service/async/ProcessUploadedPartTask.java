package org.raincityvoices.ttrack.service.async;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.util.PrototypeBean;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents.
 */
@Slf4j
@PrototypeBean
public class ProcessUploadedPartTask extends AudioTrackTask<AudioTrackTask.Input, AudioTrackTask.Output> {

    private final String mediaLocation;

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
        MediaContent media = mediaStorage().getMedia(track().getMediaLocation());
        track().updateFileMetadata(media.metadata());
        return new Output();
    }
}

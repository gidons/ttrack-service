package org.raincityvoices.ttrack.service.tasks;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents.
 */
@Slf4j
public class ProcessUploadedPartTask extends AudioTrackTask {

    private final String mediaLocation;

    ProcessUploadedPartTask(AudioTrackDTO track, AudioTrackTaskManager factory) {
        super(track, factory);
        Preconditions.checkArgument(track.hasMedia());
        Preconditions.checkArgument(track.isPartTrack());
        this.mediaLocation = track.getMediaLocation();
    }

    @Override
    protected String getTaskType() {
        return "ProcessUploadedPart";
    }

    @Override
    protected TaskMetadata getTaskMetadata() {
        return ProcessUploadedPartMetadata.builder()
            .mediaLocation(mediaLocation)
            .build();
    }

    @Override
    protected void doInitialize() throws Exception {
        if (!mediaStorage().exists(mediaLocation)) {
            throw new RuntimeException("No media exists at expected location " + mediaLocation);
        }
    }

    @Override
    protected AudioTrackDTO process() throws Exception {
        if (!mediaLocation.equals(track().getMediaLocation())) {
            log.warn("Current track media location ({}) is different from what it was at task creation ({}). Will use current value.");
        }
        MediaContent media = mediaStorage().getMedia(track().getMediaLocation());
        track().updateFileMetadata(media.metadata());
        songStorage().writeTrack(track());
        return track();
    }
}

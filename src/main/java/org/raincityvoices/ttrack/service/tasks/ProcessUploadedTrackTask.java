package org.raincityvoices.ttrack.service.tasks;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaContent;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents, including the duration (in the table) and content type (on the blob).
 * This allows us to upload content directly to the blob without first saving it to a
 * local file.
 */
@Slf4j
public class ProcessUploadedTrackTask extends AudioTrackTask {

    private final String mediaLocation;

    ProcessUploadedTrackTask(AudioTrackDTO track, AudioTrackTaskFactory factory) {
        super(track, factory);
        Preconditions.checkArgument(track.hasMedia());
        this.mediaLocation = track.getMediaLocation();
    }

    @Override
    protected String getTaskType() {
        return "ProcessUploadedTrackTask";
    }

    @Override
    protected TaskMetadata getTaskMetadata() {
        return ProcessUploadedTrackMetadata.builder()
            .mediaLocation(mediaLocation)
            .originalFileName(null)  // Not available at task creation
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
        AudioFileFormat format = AudioSystem.getAudioFileFormat(media.stream());
        FileMetadata metadata = FileMetadata.fromAudioFileFormat(format);
        track().updateFileMetadata(metadata);
        songStorage().writeTrack(track());
        return track();
    }
}

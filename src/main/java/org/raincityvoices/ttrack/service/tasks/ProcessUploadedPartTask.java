package org.raincityvoices.ttrack.service.tasks;

import java.io.IOException;
import java.util.List;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents, and starts tasks to recreate any mixes that involve that part.
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
        updateMetadata();
        recreateMixes();
        return track();
    }

    private void recreateMixes() {
        final String partName = trackId();
        List<AudioTrackDTO> tracksToRecreate = songStorage().listMixesForSong(songId())
            .stream()
            .filter(mt -> mt.getParts().contains(partName))
            .toList();
        log.info("Need to recreate {} tracks containing the new {} part.", tracksToRecreate.size(), partName);
        tracksToRecreate.forEach(t -> {
            log.debug("Launching task to recreate mix track {}", t.getId());
            manager().scheduleCreateMixTrackTask(t);
        });
    }

    private void updateMetadata() throws UnsupportedAudioFileException, IOException {
        MediaContent media = mediaStorage().getMedia(track().getMediaLocation());
        track().updateFileMetadata(media.metadata());
        songStorage().writeTrack(track());
    }
}

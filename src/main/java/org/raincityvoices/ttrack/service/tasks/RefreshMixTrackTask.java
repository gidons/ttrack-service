package org.raincityvoices.ttrack.service.tasks;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RefreshMixTrackTask extends MixTrackTaskBase {

    public RefreshMixTrackTask(AudioTrackDTO track, AudioTrackTaskFactory factory) {
        super(track, factory);
    }

    public static class Metadata implements TaskMetadata {
        // no task-specific metadata to store
    }
    
    @Override
    public String toString() {
        return String.format("[RefreshMixTrackTask: target=%s/%s]", songId(), trackId());
    }

    @Override
    protected String getTaskType() {
        return "RefreshMixTrack";
    }

    @Override
    protected TaskMetadata getTaskMetadata() {
        return new Metadata();
    }

    @Override
    protected void doInitialize() {
        describeTrackOrThrow(trackId());
    }

    @Override
    public AudioTrackDTO process() throws Exception {
        log.info("Refreshing mix track: {}", mixTrack());

        return performMix();
    }
}

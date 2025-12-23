package org.raincityvoices.ttrack.service.tasks;

import org.raincityvoices.ttrack.service.Conversions;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateMixTrackTask extends MixTrackTaskBase {
    private final MixInfo mixInfo;

    CreateMixTrackTask(AudioTrackDTO mixTrack, AudioTrackTaskManager factory) {
        super(mixTrack, factory);
        this.mixInfo = Conversions.toMixTrack(mixTrack).mixInfo();
    }

    @Override
    public String toString() {
        return String.format("[CreateMixTrackTask: target=%s/%s mixInfo=%s]", songId(), trackId(), mixInfo);
    }

    @Override
    protected String getTaskType() {
        return "CreateMixTrack";
    }

    @Override
    protected TaskMetadata getTaskMetadata() {
        return CreateMixTrackMetadata.builder()
            .mixInfo(mixInfo)
            .build();
    }

    @Override
    protected void doInitialize() {
        describeTrackOrThrow(trackId());
    }

    @Override
    public AudioTrackDTO process() throws Exception {
        log.info("Processing mix track: {}", mixTrack());

        mixTrack().setMixInfo(mixInfo);
        
        return performMix();
    }
}


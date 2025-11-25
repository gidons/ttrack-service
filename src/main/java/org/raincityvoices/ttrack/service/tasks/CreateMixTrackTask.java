package org.raincityvoices.ttrack.service.tasks;

import java.io.IOException;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.audio.AudioMixingStream;
import org.raincityvoices.ttrack.service.audio.TarsosStreamAdapter;
import org.raincityvoices.ttrack.service.audio.TarsosUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;

import com.azure.cosmos.implementation.guava25.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateMixTrackTask extends MixTrackTaskBase {
    private final MixInfo mixInfo;

    CreateMixTrackTask(AudioTrackDTO mixTrack, AudioTrackTaskFactory factory) {
        super(mixTrack, factory);
        this.mixInfo = SongController.toMixTrack(mixTrack).mixInfo();
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


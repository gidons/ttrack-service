package org.raincityvoices.ttrack.service;

import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.AudioMixingStream;
import org.raincityvoices.ttrack.service.audio.TarsosStreamAdapter;
import org.raincityvoices.ttrack.service.audio.TarsosUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;

import com.azure.cosmos.implementation.guava25.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateMixTrackTask extends AudioTrackTask {
    private final MixInfo mixInfo;
    private List<AudioTrackDTO> partTracks;

    CreateMixTrackTask(AudioTrackDTO mixTrack, SongStorage storage, MediaStorage mediaStorage, AudioDebugger.Settings debugSettings) {
        super(mixTrack, storage, mediaStorage, debugSettings);
        this.mixInfo = SongController.toMixTrack(mixTrack).mixInfo();
    }

    private final AudioTrackDTO mixTrack() { return track(); }

    @Override
    public String toString() {
        return String.format("[CreateMixTrackTask: target=%s/%s mixInfo=%s]", songId(), trackId(), mixInfo);
    }

    @Override
    protected void initialize() {
        // Override the previous mix info (if any) with the requested mix.
        mixTrack().setMixInfo(mixInfo);
        partTracks = mixInfo.parts().stream().map(AudioPart::value).map(this::describeTrackOrThrow).toList();
        partTracks.forEach(pt -> {
            Preconditions.checkArgument(pt.hasMedia(), "Track %s/%s has no audio", pt.getSongId(), pt.getId());
        });
    }

    @SuppressWarnings("resource")
    @Override
    public AudioTrackDTO process() throws Exception {
        log.info("Processing mix track: {}", mixTrack());
        
        AudioInputStream[] inputStreams = new AudioInputStream[numParts()];
        boolean needAudioMod = (mixTrack().getPitchShift() != 0 || mixTrack().getSpeedFactor() != 1.0);
        for (int i = 0; i < numParts(); ++i) {
            AudioTrackDTO partTrack = partTracks.get(i);
            log.info("Reading media for part {} from {}", partTrack.getId(), partTrack.getMediaLocation());
            MediaContent content = mediaStorage().getMedia(partTrack.getMediaLocation());
            inputStreams[i] = AudioSystem.getAudioInputStream(content.stream());
            if (needAudioMod) {
                log.info("Applying pitch shift {}, speed factor {}", mixTrack().getPitchShift(), mixTrack().getSpeedFactor());
                // TODO The adapter thread can leak here. Need to make sure we close() all of these.
                inputStreams[i] = new TarsosStreamAdapter(
                    TarsosUtils.getPitchAndSpeedDispatcher(inputStreams[i], mixTrack().getPitchShift(), mixTrack().getSpeedFactor()), debugSettings())
                               .getAudioInputStream();
            }
        }

        AudioMixingStream mixingStream = AudioMixingStream.create(inputStreams, mixTrack().getAudioMix());
        AudioTrackDTO uploaded = uploadStream(mixingStream, null);
        log.info("Uploaded mixed audio to {}", uploaded.getMediaLocation());
        return uploaded;
    }

    private int numParts() { return track().getParts().size(); }
}

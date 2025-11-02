package org.raincityvoices.ttrack.service;

import java.io.File;
import java.util.List;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.raincityvoices.ttrack.service.audio.AudioMixingStream;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.TempFile;

import com.azure.cosmos.implementation.guava25.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateMixTrackTask extends AudioTrackTask {
    private final List<AudioTrackDTO> partTracks;

    public CreateMixTrackTask(AudioTrackDTO mixTrack, List<AudioTrackDTO> partTracks, SongStorage storage) {
        super(mixTrack, storage);
        this.partTracks = List.copyOf(partTracks);
    }

    private final AudioTrackDTO mixTrack() { return track(); }

    @Override
    protected void validate() {
        partTracks.forEach(pt -> {
            Preconditions.checkArgument(pt.isPartTrack(), "Track %s/%s is not a part", pt.getSongId(), pt.getId());
            Preconditions.checkArgument(pt.hasMedia(), "Track %s/%s has no audio", pt.getSongId(), pt.getId());
        });
    }

    @Override
    public AudioTrackDTO process() throws Exception {
        log.info("Processing mix track: {}", mixTrack());

        AudioInputStream[] inputStreams = new AudioInputStream[numParts()];
        for (int i = 0; i < numParts(); ++i) {
            AudioTrackDTO partTrack = partTracks.get(i);
            log.info("Reading media for part {} from {}", partTrack.getId(), partTrack.getBlobName());
            MediaContent content = storage().downloadMedia(partTrack);
            inputStreams[i] = AudioSystem.getAudioInputStream(content.stream());
        }
        AudioMixingStream mixingStream = AudioMixingStream.create(inputStreams, mixTrack().getAudioMix());
        try (TempFile tf = new TempFile("mix", ".wav")) {
            File tempFile = tf.file();
            log.info("Mixing audio to file {}...", tempFile);
            AudioSystem.write(mixingStream, Type.WAVE, tempFile);

            AudioTrackDTO uploaded = uploadFile(tempFile, null);
            log.info("Uploaded mixed audio to {}", uploaded.getBlobName());
            return uploaded;
        }
    }

    private int numParts() { return track().getParts().size(); }
}

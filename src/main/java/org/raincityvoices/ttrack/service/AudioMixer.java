package org.raincityvoices.ttrack.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFileFormat.Type;

import org.raincityvoices.ttrack.service.api.MixTrack;
import org.raincityvoices.ttrack.service.api.PartTrack;
import org.raincityvoices.ttrack.service.audio.AudioMixingStream;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class AudioMixer implements Callable<MixTrack> {

    private final MixTrack mixTrack;
    private final List<PartTrack> partTracks;
    private final SongStorage songStorage;

    @Override
    public MixTrack call() throws Exception {
        log.info("Processing mix track: {}", mixTrack);

        AudioInputStream[] inputStreams = new AudioInputStream[numParts()];
        for (int i = 0; i < numParts(); ++i) {
            PartTrack partTrack = partTracks.get(i);
            log.info("Reading media for part {} from {}", partTrack.part(), partTrack.blobName());
            MediaContent content = songStorage.readMedia(partTrack.blobName());
            inputStreams[i] = AudioSystem.getAudioInputStream(content.stream());
        }
        AudioMixingStream mixingStream = AudioMixingStream.create(inputStreams, mixTrack.mix());
        File tempFile = File.createTempFile("ttrack-service-mix-", ".wav");
        tempFile.deleteOnExit();
        try {
            log.info("Mixing audio to file {}...", tempFile);
            AudioSystem.write(mixingStream, Type.WAVE, tempFile);
        } catch(Exception e) {
            throw new ErrorResponseException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        try (InputStream tempStream = new FileInputStream(tempFile)) {
            AudioTrackDTO uploaded = songStorage.uploadTrackAudio(AudioTrackDTO.fromMixTrack(mixTrack), 
                new MediaContent(tempStream, FileMetadata.builder()
                    .contentType("audio/wav")
                    .build()));
            log.info("Uploaded mixed audio to {}", uploaded.getBlobName());
            return uploaded.toMixTrack();
        } finally {
            tempFile.delete();
        }
    }

    private int numParts() { return mixTrack.parts().size(); }
}

package org.raincityvoices.ttrack.service.tasks;

import java.io.IOException;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.audio.AudioMixingStream;
import org.raincityvoices.ttrack.service.audio.TarsosStreamAdapter;
import org.raincityvoices.ttrack.service.audio.TarsosUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;

import com.azure.cosmos.implementation.guava25.base.Preconditions;

import javazoom.spi.mpeg.sampled.file.MpegFileFormatType;
import lombok.extern.slf4j.Slf4j;
import vavi.sound.sampled.mp3.MpegAudioFileWriter;

@Slf4j
public abstract class MixTrackTaskBase extends AudioTrackTask {

    private List<AudioTrackDTO> partTracks;

    public MixTrackTaskBase(AudioTrackDTO track, AudioTrackTaskManager factory) {
        super(track, factory);
    }

    @Override
    protected void doInitialize() throws Exception {
    }

    protected final AudioTrackDTO mixTrack() { return track(); }

    protected boolean isMp3() {
        return track().getAudioMix().numOutputs() <= 2;
    }

    protected AudioFileFormat.Type targetFileType() {
        return isMp3() ? MpegAudioFileWriter.MP3 : AudioFileFormat.Type.WAVE;
    }

    protected AudioTrackDTO performMix() throws UnsupportedAudioFileException, IOException {
        MixInfo mixInfo = SongController.toMixTrack(track()).mixInfo();
        int numParts = mixInfo.parts().size();
        partTracks = mixInfo.parts().stream().map(AudioPart::value).map(this::describeTrackOrThrow).toList();
        partTracks.forEach(pt -> {
            Preconditions.checkArgument(pt.hasMedia(), "Track %s/%s has no audio", pt.getSongId(), pt.getId());
        });
        TarsosStreamAdapter[] adapters = new TarsosStreamAdapter[numParts];
        try {
            AudioInputStream[] inputStreams = new AudioInputStream[numParts];
            boolean needAudioMod = (mixTrack().getPitchShift() != 0 || mixTrack().getSpeedFactor() != 1.0);
            for (int i = 0; i < numParts; ++i) {
                AudioTrackDTO partTrack = partTracks.get(i);
                log.info("Reading media for part {} from {}", partTrack.getId(), partTrack.getMediaLocation());
                MediaContent content = mediaStorage().getMedia(partTrack.getMediaLocation());
                inputStreams[i] = AudioFormats.toPcmStream(AudioSystem.getAudioInputStream(content.stream()));
                if (needAudioMod) {
                    log.info("Applying pitch shift {}, speed factor {}", mixTrack().getPitchShift(), mixTrack().getSpeedFactor());
                    adapters[i] = new TarsosStreamAdapter(
                        TarsosUtils.getPitchAndSpeedDispatcher(inputStreams[i], mixTrack().getPitchShift(), mixTrack().getSpeedFactor()), debugSettings());
                    inputStreams[i] = adapters[i].getAudioInputStream();
                }
            }

            AudioMixingStream mixingStream = AudioMixingStream.create(inputStreams, mixTrack().getAudioMix());
            AudioTrackDTO uploaded = uploadStream(mixingStream, generateMixFileName(), targetFileType());
            log.info("Uploaded mixed audio to {}", uploaded.getMediaLocation());
            return uploaded;
        } finally {
            for (TarsosStreamAdapter adapter : adapters) {
                if (adapter != null) {
                    // closing the adapter also closes the stream
                    adapter.close();
                }
            }
        }
    }

    private String generateMixFileName() {
        return String.format("%s - %s.%s", song().getTrackPrefix(), mixTrack().getId(), targetFileType().getExtension());
    }
}

package org.raincityvoices.ttrack.service.async;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.ChannelExtractingStream;
import org.raincityvoices.ttrack.service.audio.TargetChannelDetector;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.storage.media.MediaContent;
import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;
import org.raincityvoices.ttrack.service.util.PrototypeBean;

import com.google.common.base.Preconditions;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import vavi.sound.sampled.mp3.MpegAudioFileWriter;

/**
 * An asynchronous task that validates the media uploaded for a part track and turns it into
 * the part's mono audio.
 *
 * <p>The uploaded media must be a WAV or MP3 file with one or two channels:
 * <ul>
 * <li>Mono media is kept as is, and the track's metadata is refreshed from it.
 * <li>Stereo media is expected to carry the part on one channel (the <em>target channel</em>).
 * That channel is extracted and stored in place of the upload, in the same container format.
 * The target channel is the one given in the task input, or - if none was given - the one
 * identified by {@link TargetChannelDetector}.
 * </ul>
 *
 * <p>If the media can't be used, the task fails with a message describing the problem, the
 * track is left without media, and the uploaded blob is deleted.
 *
 * <p>All of this happens here, rather than synchronously in the upload request, because the
 * upload streams the media straight to cloud storage; keeping it that way lets this task run
 * on a different host than the one that received the upload.
 */
@Slf4j
@PrototypeBean
public class ProcessUploadedPartTask extends AudioTrackTask<ProcessUploadedPartTask.Input, AudioTrackTask.Output> {

    /** The target channel of a stereo upload: left. */
    public static final int LEFT = 0;
    /** The target channel of a stereo upload: right. */
    public static final int RIGHT = 1;

    @Data
    @NoArgsConstructor
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Input extends AudioTrackTask.Input {
        /** The target channel of a stereo upload (0 = left, 1 = right), or null to identify it automatically. */
        private Integer channel;

        public Input(AudioTrackDTO track, Integer channel) {
            super(track);
            this.channel = channel;
        }
    }

    /** Thrown when the uploaded media can't be used as a part. The message is intended for the uploader. */
    public static class InvalidUploadException extends Exception {
        public InvalidUploadException(String message) {
            super(message);
        }
    }

    /** Process an upload, identifying the target channel automatically if the media is stereo. */
    ProcessUploadedPartTask(AudioTrackDTO track) {
        super(new Input(checked(track), null));
    }

    /** Process an upload, using the given target channel if the media is stereo. */
    ProcessUploadedPartTask(AudioTrackDTO track, int channel) {
        super(new Input(checked(track), checkedChannel(channel)));
    }

    private static AudioTrackDTO checked(AudioTrackDTO track) {
        Preconditions.checkArgument(track.hasMedia());
        Preconditions.checkArgument(track.isPartTrack());
        return track;
    }

    private static int checkedChannel(int channel) {
        Preconditions.checkArgument(channel == LEFT || channel == RIGHT, "Channel must be 0 (left) or 1 (right) but was %s", channel);
        return channel;
    }

    @Override
    protected String getTaskType() {
        return "ProcessUploadedPart";
    }

    @Override
    public Class<Input> getInputClass() {
        return Input.class;
    }

    @Override
    protected void doInitialize() throws Exception {
        super.doInitialize();
        if (!mediaStorage().exists(track().getMediaLocation())) {
            throw new RuntimeException("No media exists at expected location " + track().getMediaLocation());
        }
    }

    @Override
    protected Output processTrack() throws Exception {
        String location = track().getMediaLocation();
        try {
            processMedia(location);
        } catch (InvalidUploadException e) {
            log.warn("Rejecting media uploaded for {}: {}", trackFqId(), e.getMessage());
            discardMedia(location);
            throw e;
        }
        return new Output();
    }

    private void processMedia(String location) throws Exception {
        MediaContent media = mediaStorage().getMedia(location);
        final AudioFileFormat fileFormat;
        try (InputStream in = markable(media.stream())) {
            fileFormat = AudioSystem.getAudioFileFormat(in);
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            // Format readers don't consistently report data they don't recognize as an
            // UnsupportedAudioFileException: the MP3 reader, for instance, fails with other
            // exceptions on arbitrary bytes. The media is read from local storage at this point, so
            // a failure to read it means the uploaded data is not usable, rather than an I/O problem.
            log.info("Uploaded media for {} was not recognized as audio", trackFqId(), e);
            throw new InvalidUploadException("The uploaded file is not a recognizable audio file; only WAV and MP3 are supported.");
        }
        AudioFileFormat.Type containerType = containerType(fileFormat);
        int channels = fileFormat.getFormat().getChannels();
        log.info("Uploaded media for {} is {} with {} channel(s)", trackFqId(), fileFormat.getType(), channels);

        if (channels == 1) {
            if (input().getChannel() != null) {
                log.info("Ignoring channel {} for mono media", input().getChannel());
            }
            track().updateFileMetadata(media.metadata());
        } else if (channels == 2) {
            int channel = input().getChannel() != null ? input().getChannel() : identifyTargetChannel(location);
            log.info("Extracting channel {} of stereo media for {}", channel, trackFqId());
            extractChannel(location, channel, containerType, media.metadata().fileName());
        } else {
            throw new InvalidUploadException(
                "The uploaded audio has " + channels + " channels; only mono (1) or stereo (2) are supported.");
        }
    }

    private static AudioFileFormat.Type containerType(AudioFileFormat fileFormat) throws InvalidUploadException {
        switch (fileFormat.getType().toString()) {
            case "WAVE":
                return AudioFileFormat.Type.WAVE;
            case "MP3":
                return MpegAudioFileWriter.MP3;
            default:
                throw new InvalidUploadException(
                    "The uploaded audio is in unsupported format " + fileFormat.getType() + "; only WAV and MP3 are supported.");
        }
    }

    private int identifyTargetChannel(String location) throws Exception {
        MediaContent media = mediaStorage().getMedia(location);
        try (InputStream in = markable(media.stream());
             AudioInputStream pcm = openPcm16(in)) {
            final TargetChannelDetector.Result result;
            try {
                result = TargetChannelDetector.detect(pcm);
            } catch (IOException e) {
                // As above, the data is read from local storage, so this means it is damaged.
                log.info("Uploaded audio for {} can't be read completely", trackFqId(), e);
                throw new InvalidUploadException("The uploaded audio is damaged and can't be read completely: " + e.getMessage());
            }
            if (!result.identified()) {
                throw new InvalidUploadException(
                    "Unable to determine which channel of the stereo upload carries the part ("
                    + describeFailure(result) + "). Upload again and specify the channel explicitly (0 = left, 1 = right).");
            }
            return result.channel().getAsInt();
        }
    }

    private static String describeFailure(TargetChannelDetector.Result result) {
        switch (result.outcome()) {
            case INSUFFICIENT_AUDIO:
                return "the audio is too short or too quiet to analyze";
            default:
                return String.format("neither channel is clearly the part: the left channel carries a clear pitch in %.0f%% "
                    + "and the right in %.0f%% of the audio", result.leftPitchedFraction() * 100, result.rightPitchedFraction() * 100);
        }
    }

    private void extractChannel(String location, int channel, AudioFileFormat.Type containerType, String fileName)
            throws Exception {
        MediaContent media = mediaStorage().getMedia(location);
        try (InputStream in = markable(media.stream());
             AudioInputStream pcm = openPcm16(in)) {
            uploadStream(new ChannelExtractingStream(pcm, channel, AudioDebugger.Settings.NONE), fileName, containerType);
        }
    }

    /** Decode the media to 16-bit PCM. The caller must close both the returned stream and {@code in}. */
    private static AudioInputStream openPcm16(InputStream in) throws InvalidUploadException {
        try {
            return AudioFormats.toPcm16Stream(AudioSystem.getAudioInputStream(in));
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            log.info("Uploaded audio can't be decoded", e);
            throw new InvalidUploadException("The uploaded audio can't be decoded: " + e.getMessage());
        }
    }

    private static InputStream markable(InputStream in) {
        return in.markSupported() ? in : new BufferedInputStream(in);
    }

    /** Leave the track without media, and delete the rejected blob. */
    private void discardMedia(String location) {
        track().setMediaLocation(null);
        track().setDurationSec(null);
        try {
            mediaStorage().deleteMedia(location);
        } catch (RuntimeException e) {
            log.warn("Failed to delete rejected media at {}", location, e);
        }
    }
}

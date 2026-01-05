package org.raincityvoices.ttrack.service.async;

import static com.google.common.base.MoreObjects.firstNonNull;

import java.beans.Transient;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.exceptions.ConflictException;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.Temp;
import org.springframework.beans.factory.annotation.Autowired;

import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter(AccessLevel.PROTECTED)
@Accessors(fluent = true)
public abstract class AudioTrackTask<I extends AudioTrackTask.Input, O extends AudioTrackTask.Output> extends AsyncTask<I, O> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    @Accessors(fluent = false)
    public static class Input extends AsyncTask.Input {
        private String trackId;
        // At this time, debug settings are not persisted, and are only used in synchronous use-cases.
        @Getter(onMethod = @__(@Transient))
        private AudioDebugger.Settings debugSettings;

        public Input(AudioTrackDTO trackDto) {
            super(trackDto.getSongId());
            this.trackId = trackDto.getId();
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = false)
    public static class Output extends AsyncTask.Output {
        /** The ETag of the final track written. */
        private String trackETag;
    }

    private static final Duration MAX_WAIT_FOR_LOCK = Duration.ofSeconds(30);
    private static final Duration LOCK_POLL_INTERVAL = Duration.ofMillis(3000);

    @Autowired
    private SongStorage songStorage;
    @Autowired
    private MediaStorage mediaStorage;
    @Autowired
    private FileManager fileManager;

    private SongDTO song;
    private AudioTrackDTO track;

    protected AudioTrackTask(I input) {
        super(input);
    }

    protected String trackId() { return input().getTrackId(); }
    protected String trackFqId() {
        return AudioTrackDTO.fqId(songId(), trackId());
    }
    protected String fqId() {
        return String.format("%s:%s", trackFqId(), taskId());
    }
    protected AudioDebugger.Settings debugSettings() { return firstNonNull(input().getDebugSettings(), AudioDebugger.Settings.NONE); }

    /**
     * Perform synchronous initializations and validations, and then persist the
     * task in the DB.
     * 
     * @throws Exception
     */
    @Override
    protected void doInitialize() throws Exception {
        song = songStorage.describeSong(songId());
        if (song == null) {
            throw new IllegalArgumentException(String.format("Song %s does not exist.", songId()));
        }
        track = describeTrackOrThrow(trackId());
    }

    @Override
    public O process() throws Exception {
        track = describeTrackOrThrow(trackId());
        song = songStorage.describeSong(songId());
        O output = processTrack();
        track = songStorage().writeTrack(track);
        output.setTrackETag(track.getETag());
        return output;
    }

    /**
     * Perform the main processing.
     * 
     * @return the final track DTO that should be persisted.
     */
    protected abstract O processTrack() throws Exception;

    protected Duration getLockTimeout() { return MAX_WAIT_FOR_LOCK; }

    @Override
    protected boolean waitForLock() throws InterruptedException {
        Instant timeLimit = clock().instant().plus(getLockTimeout());
        refreshTrack();
        while (true) {
            while (track.getCurrentTaskId() != null) {
                if (clock().instant().isAfter(timeLimit)) {
                    return false;
                }
                log.info("Track {} is locked by task {}. Waiting...", trackFqId(), track.getCurrentTaskId());
                Thread.sleep(LOCK_POLL_INTERVAL);
                refreshTrack();
            }
            // attempt to lock
            track.setCurrentTaskId(taskId());
            try {
                songStorage().writeTrack(track);
                // Great, we got the lock.
                return true;
            } catch(ConflictException e) {
                // Ugh, somebody else did something. Keep trying.
                refreshTrack();
            }
        }
    }

    @Override
    protected void releaseLock() {
        track().setCurrentTaskId(null);
        songStorage().writeTrack(track());
    }

    protected void refreshTrack() {
        track = describeTrackOrThrow(trackId());
    }

    protected AudioTrackDTO describeTrackOrThrow(String trackId) {
        AudioTrackDTO dto = songStorage().describeTrack(songId(), trackId);
        if (dto == null) {
            throw new IllegalArgumentException(String.format("Track %s/%s does not exist.", songId(), trackId));
        }
        return dto;
    }

    protected AudioTrackDTO uploadStream(AudioInputStream stream, String originalFileName, AudioFileFormat.Type targetFormat) {
        if (track().getMediaLocation() == null) {
            track().setMediaLocation(mediaStorage.locationFor(new SongId(track.getSongId()), track.getId()));
        }
        log.info("Processing audio to upload to {}", track().getMediaLocation());

        try(Temp.File tempFile = Temp.file("ttrack-mix-")) {
            log.info("Writing audio as {}...", targetFormat);
            AudioInputStream formattedStream = AudioFormats.toTargetFormat(stream, targetFormat);
            FileMetadata metadata = FileMetadata.builder().fileName(originalFileName).build();
            fileManager.writeAudio(formattedStream, targetFormat, tempFile);
            InputStream mediaStream = new FileInputStream(tempFile);
            log.info("Uploading audio to {}...", track.getMediaLocation());
            mediaStorage().putMedia(track().getMediaLocation(), new MediaContent(mediaStream, metadata));
            log.info("Audio uploaded.");
        } catch (Exception e) {
            log.error("Exception while processing mix track {}", trackFqId(), e);
            throw new RuntimeException(e);
        }
        log.info("Updating track metadata");
        FileMetadata updatedMetadata = mediaStorage().getMediaMetadata(track().getMediaLocation());
        log.debug("New metadata: {}", updatedMetadata);
        track().updateFileMetadata(updatedMetadata);
        songStorage().writeTrack(track);
        log.info("Metadata updated.");
        return track();
    }

    protected AudioTrackDTO uploadFile(File file, String originalFileName) {
        final FileMetadata metadata = getMetadata(file, originalFileName);
        try (InputStream stream = new FileInputStream(file)) {
            log.info("Uploading audio file...");
            mediaStorage().putMedia(track().getMediaLocation(), new MediaContent(stream, metadata));
            log.info("Saving track with new metadata...");
            track.setMediaLocation(track().getMediaLocation());
            track.setDurationSec((int) metadata.durationSec());
            return track;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open file " + file + " for reading.", e);
        }
    }

    protected FileMetadata getMetadata(File file, String originalFileName) {
        final FileMetadata metadata;
        try {
            log.info("Inspecting audio format for file {}", file);
            AudioFileFormat format = fileManager().getAudioFileFormat(file);
            metadata = FileMetadata.fromAudioFileFormat(format)
                    .withFileName(StringUtils.defaultString(originalFileName, file.getName()));
            log.info("File metadata: {}", metadata);
        } catch (UnsupportedAudioFileException | IOException e) {
            throw new RuntimeException("Failed to get format for audio file " + file);
        }
        return metadata;
    }
}

package org.raincityvoices.ttrack.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.Ffmpeg;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.Temp;

import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Accessors(fluent = true)
public abstract class AudioTrackTask implements Callable<AudioTrackDTO> {

    private final String songId;
    private final String trackId;
    private final AudioDebugger.Settings debugSettings;
    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    private final FileManager fileManager;
    private final Ffmpeg ffmpeg;
    
    private AudioTrackDTO track;

    protected AudioTrackTask(AudioTrackDTO track, AudioTrackTaskFactory factory) {
        Preconditions.checkNotNull(track);
        Preconditions.checkNotNull(factory);
        this.songId = track.getSongId();
        this.trackId = track.getId();
        this.songStorage = factory.getSongStorage();
        this.mediaStorage = factory.getMediaStorage();
        this.fileManager = factory.getFileManager();
        this.ffmpeg = factory.getFfmpeg();
        this.debugSettings = factory.getDebugSettings();
    }

    @Override
    public AudioTrackDTO call() throws Exception {
        log.info("Starting task {} for track {}/{}", this, songId(), trackId());
        try {
            track = describeTrackOrThrow(trackId());
        } catch(Exception e) {
            log.error("Failed to fetch track {}/{}", songId(), trackId(), e);
            throw new RuntimeException(e);
        }
        try {
            initialize();
        } catch(Exception e) {
            log.error("Task failed validation", e);
            throw new RuntimeException(e);
        }

        try {
            AudioTrackDTO processed = process();
            log.info("Completed task {}", this);
            return processed;
        } catch(Exception e) {
            log.error("Task failed processing", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Perform initializations and validations that can be done relatively quickly.
     * @throws Exception if there were problems initializing or validation errors.
     */
    protected abstract void initialize() throws Exception;
    /**
     * Perform the main processing.
     * @return the final track DTO that should be persisted.
     */
    protected abstract AudioTrackDTO process() throws Exception;

    protected AudioTrackDTO describeTrackOrThrow(String trackId) {
        AudioTrackDTO dto = songStorage().describePart(songId(), trackId);
        if (dto == null) {
            throw new IllegalArgumentException(String.format("Track %s/%s does not exist.", track().getSongId(), trackId));
        }
        return dto;
    }

    protected AudioTrackDTO uploadStream(AudioInputStream stream, String originalFileName) {
        if (track().getMediaLocation() == null) {
            track().setMediaLocation(mediaStorage.mediaLocationFor(new SongId(track.getSongId()), track.getId()));
        }
        log.info("Processing audio to upload to {}", track().getMediaLocation());
        try(Temp.File tempWav = Temp.file("mix", ".wav");
            Temp.File tempMp3 = Temp.file("mix", ".mp3")) {
            log.info("Writing audio to temp file {}...", tempWav);
            fileManager.writeWavAudio(stream, tempWav);
            log.info("Converting to MP3 file {}...", tempMp3);
            ffmpeg.convertToMp3(tempWav, tempMp3);
            log.info("Uploading MP3 audio file to {}...", track.getMediaLocation());
            InputStream mediaStream = new BufferedInputStream(new FileInputStream(tempMp3));
            FileMetadata metadata = FileMetadata.builder().fileName(originalFileName).build();
            mediaStorage().putMedia(track().getMediaLocation(), new MediaContent(mediaStream, metadata));
            log.info("Audio uploaded.");
        } catch(Exception e) {
            log.error("Exception while processing mix track {}/{}", songId(), trackId(), e);
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
            track.setDurationSec((int)metadata.durationSec());
            return track;
        } catch(IOException e) {
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

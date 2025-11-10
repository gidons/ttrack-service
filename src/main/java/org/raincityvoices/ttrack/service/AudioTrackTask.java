package org.raincityvoices.ttrack.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;

import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Accessors(fluent = true)
public abstract class AudioTrackTask implements Callable<AudioTrackDTO> {

    private final AudioTrackDTO track;
    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    private final AudioDebugger.Settings debugSettings;
    
    protected AudioTrackTask(AudioTrackDTO track, SongStorage songStorage, MediaStorage mediaStorage) {
        this(track, songStorage, mediaStorage, AudioDebugger.Settings.NONE);
    }

    protected AudioTrackTask(AudioTrackDTO track, SongStorage songStorage, MediaStorage mediaStorage, AudioDebugger.Settings debugSettings) {
        // defensively clone the track
        // TODO need to do optimistic locking?
        Preconditions.checkNotNull(track);
        Preconditions.checkNotNull(songStorage);
        this.track = track.toBuilder().build();
        this.songStorage = songStorage;
        this.mediaStorage = mediaStorage;
        this.debugSettings = debugSettings;
    }

    @Override
    public AudioTrackDTO call() throws Exception {
        log.info("Starting task {}", this);
        validate();

        AudioTrackDTO processed = process();

        log.info("Completed task {}", this);
        return processed;
    }

    protected abstract void validate() throws Exception;
    protected abstract AudioTrackDTO process() throws Exception;

    protected AudioTrackDTO uploadStream(InputStream stream, String originalFileName) {
        if (track().getMediaLocation() == null) {
            track().setMediaLocation(mediaStorage.mediaLocationFor(new SongId(track.getSongId()), track.getId()));
        }
        log.info("Uploading audio file to {}...", track.getMediaLocation());
        FileMetadata metadata = FileMetadata.builder()
            .fileName(originalFileName)
            .build();
        mediaStorage().putMedia(track().getMediaLocation(), new MediaContent(stream, metadata));
        log.info("Audio uploaded.");
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
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
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
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file);
            metadata = FileMetadata.fromAudioFileFormat(format)
                                   .withFileName(StringUtils.defaultString(originalFileName, file.getName()));
            log.info("File metadata: {}", metadata);
        } catch (UnsupportedAudioFileException | IOException e) {
            throw new RuntimeException("Failed to get format for audio file " + file);
        }
        return metadata;
    }
}

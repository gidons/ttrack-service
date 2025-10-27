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

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
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
    private final SongStorage storage; 

    protected AudioTrackTask(AudioTrackDTO track, SongStorage storage) {
        // defensively clone the track
        // TODO need to do optimistic locking?
        Preconditions.checkNotNull(track);
        Preconditions.checkNotNull(storage);
        this.track = track.toBuilder().build();
        this.storage = storage;
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

    protected AudioTrackDTO uploadFile(File file, String originalFileName) {
        final FileMetadata metadata = getMetadata(file, originalFileName);
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            log.info("Uploading audio file...");
            AudioTrackDTO uploaded = storage().uploadTrackAudio(track(), new MediaContent(stream, metadata));
            return uploaded;
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

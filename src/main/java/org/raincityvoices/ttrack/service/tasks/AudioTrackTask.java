package org.raincityvoices.ttrack.service.tasks;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.Ffmpeg;
import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.raincityvoices.ttrack.service.util.Temp;

import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter(AccessLevel.PROTECTED)
@Accessors(fluent = true)
public abstract class AudioTrackTask implements Callable<AudioTrackDTO> {

    private static final ObjectMapper MAPPER = JsonUtils.newMapper();
    
    @Getter(AccessLevel.PUBLIC)
    private final String taskId;
    private final String songId;
    private final String trackId;
    private final AudioDebugger.Settings debugSettings;
    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    private final AsyncTaskStorage asyncTaskStorage;
    private final FileManager fileManager;
    private final Ffmpeg ffmpeg;

    private AudioTrackDTO track;
    private AsyncTaskDTO asyncTask;
    private final Clock clock;

    protected AudioTrackTask(AudioTrackDTO track, AudioTrackTaskFactory factory) {
        this(track.getSongId(), track.getId(), factory);
    }

    protected AudioTrackTask(String songId, String trackId, AudioTrackTaskFactory factory) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);
        Preconditions.checkNotNull(factory);
        this.taskId = UUID.randomUUID().toString();
        this.songId = songId;
        this.trackId = trackId;
        this.songStorage = factory.getSongStorage();
        this.mediaStorage = factory.getMediaStorage();
        this.fileManager = factory.getFileManager();
        this.ffmpeg = factory.getFfmpeg();
        this.asyncTaskStorage = factory.getAsyncTaskStorage();
        this.debugSettings = factory.getDebugSettings();
        this.clock = Clock.systemUTC();
    }

    private String trackFqId() {
        return AudioTrackDTO.fqId(songId, trackId);
    }

    private String fqId() {
        return String.format("%s:%s", trackFqId(), taskId());
    }

    /**
     * Perform synchronous initializations and validations, and then persist the
     * task in the DB.
     * 
     * @throws Exception
     */
    public void initialize() throws Exception {
        doInitialize();
        createAsyncTaskRecord();
    }

    @Override
    public AudioTrackDTO call() throws Exception {
        log.info("Starting task {} for track {}", this, trackFqId());
        try {
            updateAsyncTaskRunning();
        } catch (Exception e) {
            log.error("Failed to update task status for task {}", taskId(), e);
            throw new RuntimeException(e);
        }

        try {
            track = describeTrackOrThrow(trackId());
        } catch (Exception e) {
            log.error("Failed to fetch track {}", trackFqId(), e);
            updateAsyncTaskFailed(e);
            throw new RuntimeException(e);
        }

        try {
            AudioTrackDTO processed = process();
            updateAsyncTaskSucceeded();
            log.info("Completed task {}", this);
            return processed;
        } catch (Exception e) {
            log.error("Task failed processing", e);
            updateAsyncTaskFailed(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Create and persist an async task record in the database.
     * The task is initially created with status PENDING.
     */
    private void createAsyncTaskRecord() {
        TaskMetadata metadata = getTaskMetadata();

        asyncTask = AsyncTaskDTO.builder()
                .taskId(taskId)
                .status(AsyncTaskDTO.PENDING)
                .taskType(getTaskType())
                .startTime(Instant.now(clock))
                .metadata(metadata)
                .build();

        asyncTaskStorage.createTask(asyncTask);
        log.info("Created async task record: {}", taskId);
    }

    /**
     * Update async task status to RUNNING after initialization.
     */
    private void updateAsyncTaskRunning() {
        if (asyncTask != null) {
            asyncTask.setStatus(AsyncTaskDTO.RUNNING);
            asyncTaskStorage.updateTask(asyncTask);
            log.info("Updated async task to RUNNING: {}", asyncTask.getTaskId());
        }
    }

    /**
     * Update async task status to SUCCEEDED after successful processing.
     */
    private void updateAsyncTaskSucceeded() {
        if (asyncTask != null) {
            asyncTask.setStatus(AsyncTaskDTO.SUCCEEDED);
            asyncTask.setEndTime(Instant.now(clock));
            asyncTaskStorage.updateTask(asyncTask);
            log.info("Updated async task to SUCCEEDED: {}", asyncTask.getTaskId());
        }
    }

    /**
     * Update async task status to FAILED with error details.
     */
    private void updateAsyncTaskFailed(Exception e) {
        if (asyncTask != null) {
            asyncTask.setStatus(AsyncTaskDTO.FAILED);
            asyncTask.setEndTime(Instant.now(clock));
            asyncTask.setErrorDetails(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            asyncTaskStorage.updateTask(asyncTask);
            log.info("Updated async task to FAILED: {}", asyncTask.getTaskId());
        }
    }

    /**
     * Get the task type name for this task.
     * Should be overridden by subclasses.
     */
    protected abstract String getTaskType();

    /**
     * Get task-specific metadata that should be persisted.
     * Should be overridden by subclasses to return TaskMetadata implementation.
     */
    protected abstract TaskMetadata getTaskMetadata();

    /**
     * Perform initializations and validations that can be done synchronously.
     * 
     * @throws Exception if there were problems initializing or validation errors.
     */
    protected abstract void doInitialize() throws Exception;

    /**
     * Perform the main processing.
     * 
     * @return the final track DTO that should be persisted.
     */
    protected abstract AudioTrackDTO process() throws Exception;

    protected AudioTrackDTO describeTrackOrThrow(String trackId) {
        AudioTrackDTO dto = songStorage().describeTrack(songId(), trackId);
        if (dto == null) {
            throw new IllegalArgumentException(String.format("Track %s/%s does not exist.", songId(), trackId));
        }
        return dto;
    }

    protected AudioTrackDTO uploadStream(AudioInputStream stream, String originalFileName) {
        if (track().getMediaLocation() == null) {
            track().setMediaLocation(mediaStorage.locationFor(new SongId(track.getSongId()), track.getId()));
        }
        log.info("Processing audio to upload to {}", track().getMediaLocation());
        try (Temp.File tempWav = Temp.file("mix", ".wav");
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

    @Override
    public String toString() {
        String metadataJson;
        try {
            metadataJson = MAPPER.writeValueAsString(getTaskMetadata());
        } catch(IOException e) {
            log.warn("Unable to render task metadata as JSON", e);
            metadataJson = "N/A";
        }
        return String.format("%s[taskId=%s,track=%s,metadata=%s]", 
                getClass().getSimpleName(), taskId(), trackFqId(), metadataJson);
    }
}

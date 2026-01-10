package org.raincityvoices.ttrack.service.async;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.storage.TempFileStorage;
import org.raincityvoices.ttrack.service.storage.mapper.Property;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.PrototypeBean;
import org.raincityvoices.ttrack.service.util.Temp;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ImmutableList;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(fluent = true)
@PrototypeBean
public class ZipTracksTask extends AsyncTask<ZipTracksTask.Input, ZipTracksTask.Output> {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(60);

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Accessors(fluent = false)
    @NoArgsConstructor
    public static class Input extends AsyncTask.Input {
        public Input(String songId, List<String> trackIds) {
            super(songId);
            this.trackIds = ImmutableList.copyOf(trackIds);
        }
        @Getter(onMethod = @__(@Property(type="json")))
        List<String> trackIds;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Accessors(fluent = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output extends AsyncTask.Output {
        String downloadUrl;
        Instant downloadUrlExpiry;
        String zipFileName;
    }

    @Autowired
    private FileManager fileManager;
    @Autowired
    private SongStorage songStorage;
    @Autowired
    private MediaStorage mediaStorage;
    @Autowired
    private TempFileStorage tempStorage;

    private SongDTO song;
    /** The filename that the user will see when downloading. */
    private String zipFileName;
    /** The name of the blob storing the zipped contents. */
    private String blobName;

    public ZipTracksTask(String songId, List<String> trackIds) {
        super(new Input(songId, trackIds)); 
    }

    @Override
    protected String getTaskType() {
        return "ZipAllTracks";
    }

    @Override
    public Class<Input> getInputClass() {
        return Input.class;
    }

    @Override
    protected void doInitialize() throws Exception {
        song = songStorage.describeSong(songId());
        if (song == null) {
            throw new IllegalArgumentException(String.format("Song %s does not exist.", songId()));
        }
        if (input().getTrackIds() == null || input().getTrackIds().isEmpty()) {
            throw new IllegalArgumentException("Track IDs to zip not specified.");
        }
    }

    @Override
    protected boolean waitForLock() throws Exception {
        /* No locking, since this is a read-only task as far as songs and tracks.
         * Note, however, that in principle we might run into a race condition where
         * a track is being modified as we're reading it to add it to the zip.
         * 
         * However, this is both unlikely and ultimately not very problematic;
         * the worst that can happen is that the resulting zip file is incorrect,
         * and will have to be recreated.
         * 
         * The alternative is to lock each track before reading it, but that adds
         * way too much complexity and failure modes.
         */
        return true;
    }

    @Override
    protected void releaseLock() {
    }

    @Override
    protected Output process() throws Exception {
        blobName = taskId() + ".zip";
        zipFileName = song.getShortTitleOrTitle() + ".zip";
        try(Temp.File file = fileManager.tempFile(songId(), ".zip")) {
            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                log.info("Zipping tracks for song {} to {}", songId(), file);
                for (String trackId : input().getTrackIds()) {
                    log.info("Zipping track {}/{}", songId(), trackId);
                    MediaContent media = mediaStorage.getMedia(mediaStorage.locationFor(songId(), trackId));
                    out.putNextEntry(new ZipEntry(media.metadata().fileName()));
                    IOUtils.copy(media.stream(), out);
                }
            }

            FileMetadata metadata = FileMetadata.builder()
                .contentType("application/zip")
                .fileName(zipFileName)
                .build();
            tempStorage.createFile(blobName, metadata, file);
        }
        return new Output(tempStorage.getDownloadUrl(blobName, DOWNLOAD_URL_EXPIRY), clock().instant().plus(DOWNLOAD_URL_EXPIRY), zipFileName);
    }
    
}

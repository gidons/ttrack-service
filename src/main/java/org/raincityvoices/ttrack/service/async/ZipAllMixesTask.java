package org.raincityvoices.ttrack.service.async;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.storage.TempFileStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.PrototypeBean;
import org.raincityvoices.ttrack.service.util.Temp;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter(AccessLevel.PROTECTED)
@Accessors(fluent = true)
@PrototypeBean
public class ZipAllMixesTask extends AsyncTask<AsyncTask.Input, ZipAllMixesTask.Output> {

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Accessors(fluent = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output extends AsyncTask.Output {
        String zipBblobName;
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

    public ZipAllMixesTask(String songId) {
        super(new Input(songId)); 
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
        blobName = taskId() + ".zip";
        zipFileName = song.getShortTitle() + ".zip";
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
        List<AudioTrackDTO> tracks = songStorage()
            .listMixesForSong(songId())
            .stream()
            // Exclude the "All" mix which is unlikely to be useful and is very large
            .filter(t -> !t.getId().equals(SongController.ALL_CHANNEL_MIX_ID))
            .toList();
        if (tracks.isEmpty()) {
            return null;
        }
        try(Temp.File file = fileManager.tempFile(songId(), ".zip")) {

            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                log.info("Zipping tracks for song {} to {}", songId(), file);
                for (AudioTrackDTO track : tracks) {
                    log.info("Zipping track {}", track.getFqId());
                    MediaContent media = mediaStorage().getMedia(mediaStorage().locationFor(songId(), track.getId()));
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
        return new Output(blobName, zipFileName);
    }
    
}

package org.raincityvoices.ttrack.service;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.TempFile;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UploadPartTrackTask extends AudioTrackTask {

    private final TempFile audioTempFile;
    private final String originalFileName;

    UploadPartTrackTask(AudioTrackDTO track, TempFile audioTempFile, String originalFileName, SongStorage songStorage, MediaStorage mediaStorage) {
        super(track, songStorage, mediaStorage);
        Preconditions.checkNotNull(audioTempFile);
        Preconditions.checkArgument(audioTempFile.file().isFile());
        this.audioTempFile = audioTempFile;
        this.originalFileName = originalFileName;
    }

    @Override
    protected void validate() throws Exception {
    }

    @Override
    protected AudioTrackDTO process() throws Exception {
        try(audioTempFile) {
            AudioTrackDTO uploaded = uploadFile(audioTempFile.file(), originalFileName);
            log.info("Uploaded part audio to {}", uploaded.getMediaLocation());
            return uploaded;
        }
    }
}

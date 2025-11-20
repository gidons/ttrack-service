package org.raincityvoices.ttrack.service.tasks;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.util.Temp;

import com.google.common.base.Preconditions;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UploadPartTrackTask extends AudioTrackTask {

    private final Temp.File audioTempFile;
    private final String originalFileName;

    UploadPartTrackTask(AudioTrackDTO track, Temp.File audioTempFile, String originalFileName, AudioTrackTaskFactory factory) {
        super(track, factory);
        Preconditions.checkNotNull(audioTempFile);
        Preconditions.checkArgument(audioTempFile.isFile());
        this.audioTempFile = audioTempFile;
        this.originalFileName = originalFileName;
    }

    @Override
    protected String getTaskType() {
        return "UploadPartTrackTask";
    }

    @Override
    protected TaskMetadata getTaskMetadata() {
        return UploadPartTrackMetadata.builder()
            .audioTempFilePath(audioTempFile.getAbsolutePath())
            .originalFileName(originalFileName)
            .build();
    }

    @Override
    protected void doInitialize() throws Exception {}

    @Override
    protected AudioTrackDTO process() throws Exception {
        try(audioTempFile) {
            AudioTrackDTO uploaded = uploadFile(audioTempFile, originalFileName);
            log.info("Uploaded part audio to {}", uploaded.getMediaLocation());
            return uploaded;
        }
    }
}

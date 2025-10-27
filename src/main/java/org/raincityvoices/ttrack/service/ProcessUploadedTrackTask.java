package org.raincityvoices.ttrack.service;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.TempFile;

import com.google.common.base.Preconditions;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents, including the duration (in the table) and content type (on the blob).
 * This allows us to download content directly to the blob without first saving it to a
 * local file.
 */
public class ProcessUploadedTrackTask extends AudioTrackTask {

    protected ProcessUploadedTrackTask(AudioTrackDTO track, SongStorage storage) {
        super(track, storage);
        Preconditions.checkArgument(track.hasMedia());
    }

    @Override
    protected void validate() throws Exception {}

    @Override
    protected AudioTrackDTO process() throws Exception {
        MediaContent media = storage().downloadMedia(track());
        FileMetadata metadata;
        try (TempFile tf = new TempFile("track"); 
             OutputStream tempStream = new BufferedOutputStream(new FileOutputStream(tf.file()))) {
            // Download the audio to disk
            IOUtils.copy(media.stream(), tempStream);
        
            metadata = getMetadata(tf.file(), media.metadata().fileName());
            storage().updateTrackMetadata(track(), metadata);
        }
        return null;
    }
}

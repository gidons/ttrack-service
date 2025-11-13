package org.raincityvoices.ttrack.service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;

import com.google.common.base.Preconditions;

/**
 * An aysnchronous task that updates the metadata for an uploaded audio track based on
 * the audio contents, including the duration (in the table) and content type (on the blob).
 * This allows us to upload content directly to the blob without first saving it to a
 * local file.
 */
public class ProcessUploadedTrackTask extends AudioTrackTask {

    ProcessUploadedTrackTask(AudioTrackDTO track, SongStorage songStorage, MediaStorage mediaStorage) {
        super(track, songStorage, mediaStorage);
        Preconditions.checkArgument(track.hasMedia());
    }

    @Override
    protected void initialize() throws Exception {}

    @Override
    protected AudioTrackDTO process() throws Exception {
        MediaContent media = mediaStorage().getMedia(track().getMediaLocation());
        AudioFileFormat format = AudioSystem.getAudioFileFormat(media.stream());
        FileMetadata metadata = FileMetadata.fromAudioFileFormat(format);
        track().updateFileMetadata(metadata);
        songStorage().writeTrack(track());
        return track();
    }
}

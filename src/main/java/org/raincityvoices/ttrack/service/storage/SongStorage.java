package org.raincityvoices.ttrack.service.storage;

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.util.List;

import org.raincityvoices.ttrack.service.MediaContent;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

public interface SongStorage {

    /** @return The metadata for all songs in the system (empty list if none.) */ 
    List<SongDTO> listAllSongs();
    /** @return the metadata for the given song, if it exists; null if not. */
    SongDTO describeSong(SongId songId);
    /** 
     * Update the metadata for the given song, if it exists, or create a new one if not.
     * @return the ID of the new or existing song.
     */
    SongId writeSong(SongDTO songDto);
    /** @return the metadata for all tracks created for the given song (empty list if none.) */
    List<AudioTrackDTO> listTracksForSong(SongId songId);
    default List<AudioTrackDTO> listPartsForSong(SongId songId) {
        return listTracksForSong(songId).stream()
            .filter(t -> t.isPartTrack())
            .collect(toImmutableList());
    }
    default List<AudioTrackDTO> listMixesForSong(SongId songId) {
        return listTracksForSong(songId).stream()
            .filter(t -> t.isMixTrack())
            .collect(toImmutableList());
    }
    /** @return the metadata for the given track of the given song. */
    AudioTrackDTO describeTrack(SongId songId, String trackId);
    /** @return the metadata for the given part of the given song, or null if there is no such part. */
    default AudioTrackDTO describePart(SongId songId, AudioPart part) {
        return describeTrack(songId, part.name());
    }
    /** @return the metadata for the track of the given song that has a mix with the given name, or null if there is none. */
    default AudioTrackDTO describeMix(SongId songId, String mixName) {
        return describeTrack(songId, mixName);
    }
    /**
     * Upload the audio for the given track to blob storage. If the track metadata doesn't exist yet, create it.
     * After this, the metadata will have:
     * - {@code processed} set to the upload timestamp
     * - {@code blobName} set to the location of the audio blob
     * - {@code durationSec} set to the duration of the audio. 
     * @return the created/updated track metadata, including the location of the audio in storage.
     */
    AudioTrackDTO uploadTrackAudio(AudioTrackDTO trackDto, MediaContent media);
    AudioTrackDTO writeTrack(AudioTrackDTO trackDto);
    MediaContent readMedia(String blobNam);
}

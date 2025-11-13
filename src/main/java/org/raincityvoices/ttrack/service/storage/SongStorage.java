package org.raincityvoices.ttrack.service.storage;

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.util.List;

public interface SongStorage {

    /** @return The metadata for all songs in the system (empty list if none.) */ 
    List<SongDTO> listAllSongs();
    /** @return the metadata for the given song, if it exists; null if not. */
    SongDTO describeSong(String songId);
    /** 
     * Update the metadata for the given song, if it exists, or create a new one if not.
     * @return the ID of the new or existing song.
     */
    String writeSong(SongDTO songDto);
    /** @return the metadata for all tracks created for the given song (empty list if none.) */
    List<AudioTrackDTO> listTracksForSong(String songId);
    default List<AudioTrackDTO> listPartsForSong(String songId) {
        return listTracksForSong(songId).stream()
            .filter(t -> t.isPartTrack())
            .collect(toImmutableList());
    }
    default List<AudioTrackDTO> listMixesForSong(String songId) {
        return listTracksForSong(songId).stream()
            .filter(t -> t.isMixTrack())
            .collect(toImmutableList());
    }
    /** @return the metadata for the given track of the given song. */
    AudioTrackDTO describeTrack(String songId, String trackId);
    /** @return the metadata for the given part of the given song, or null if there is no such part. */
    default AudioTrackDTO describePart(String songId, String part) {
        return describeTrack(songId, part);
    }
    /** @return the metadata for the track of the given song that has a mix with the given name, or null if there is none. */
    default AudioTrackDTO describeMix(String songId, String mixName) {
        return describeTrack(songId, mixName);
    }
    void deleteTrack(String songId, String trackId);
    AudioTrackDTO writeTrack(AudioTrackDTO trackDto);
}

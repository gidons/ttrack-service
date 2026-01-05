package org.raincityvoices.ttrack.service.storage;

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.util.List;

import org.raincityvoices.ttrack.service.exceptions.ConflictException;

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
    boolean deleteSong(String songId);
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
    boolean deleteTrack(String songId, String trackId);
    /**
     * Create or update the track metadata.
     * If an entity with the same song and track IDs doesn't exist, write this one.
     * If there is an existing entity, replace it entirely with this one, but only if the ETag hasn't changed.
     * @return the updated DTO, including the new ETag.
     * @throws ConflictException if the input ETag doesn't match the existing one or if the input ETag was empty
     * but an entity already exists for the same track.
     */
    AudioTrackDTO writeTrack(AudioTrackDTO trackDto);
}

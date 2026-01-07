package org.raincityvoices.ttrack.service.storage;

import java.beans.Transient;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.api.Song;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.storage.mapper.PartitionKey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

/**
 * A song, which can have multiple audio tracks. Persisted in the Songs table.
 * A song is created with metadata and no tracks, then tracks are added as they are created.
 */
@Data
@EqualsAndHashCode(callSuper = false) // ignore timestamp and ETag
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SongDTO extends BaseDTO {
    @Getter(onMethod=@__(@PartitionKey))
    private String id;
    /** Song title. */
    private String title;
    /** Short title used as track name prefix. */
    private String shortTitle;
    /** 
     * Version of this song, in case there are multiple (e.g. different arrangers, different interps, for different ensembles, etc.)
     * The version is free-form text, and can be empty, but should be unique per song title.
     * The combination of title + version should be unique across all songs.
     */
    @Default private String version = "";
    /** Song arranger. */
    private String arranger;
    /** Musical key, e.g. "D#" or "Db". */
    private String key;
    /** 
     * Voicing type, e.g. "SATB", "TTBB", "SSAATTBB", etc. 
     * This is mostly used by the FE to implement some sensible defaults.
     */
    private String voicing;
    /** Song duration in seconds, reflecting the original part tracks. Some mix tracks might have different duration. */
    private int durationSec;

    public String getRowKey() { return ""; }
    public void setRowKey(String rk) { if (!"".equals(rk)) throw new IllegalArgumentException("Unexpected RowKey '" + rk + "' set for SongDTO with ID '" + id + "'; expected empty string"); }

    @Transient
    public String getTrackPrefix() {
        return StringUtils.defaultIfBlank(shortTitle, title);
    }

    public static SongDTO fromSong(Song song) {
        SongDTO dto = SongDTO.builder()
            .id(song.getId().value())
            .title(song.getTitle())
            .shortTitle(song.getShortTitle())
            .version(song.getVersion())
            .arranger(song.getArranger())
            .key(song.getKey())
            .voicing(song.getVoicing())
            .durationSec(song.getDurationSec())
            .build();
        dto.setETag(song.getETag());
        return dto;
    }

    public Song toSong() {
        return Song.builder()
            .id(SongId.orNone(id))
            .title(title)
            .shortTitle(shortTitle)
            .version(version)
            .arranger(arranger)
            .key(key)
            .voicing(voicing)
            .durationSec(durationSec)
            .eTag(eTag)
            .build();
    }
}

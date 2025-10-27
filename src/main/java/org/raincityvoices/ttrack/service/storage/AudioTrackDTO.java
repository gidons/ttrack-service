package org.raincityvoices.ttrack.service.storage;

import java.beans.Transient;
import java.time.Instant;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.raincityvoices.ttrack.service.api.AudioTrack;
import org.raincityvoices.ttrack.service.api.MixTrack;
import org.raincityvoices.ttrack.service.api.PartTrack;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.mapper.BaseDTO;
import org.raincityvoices.ttrack.service.storage.mapper.PartitionKey;
import org.raincityvoices.ttrack.service.storage.mapper.Property;
import org.raincityvoices.ttrack.service.storage.mapper.RowKey;
import org.raincityvoices.ttrack.service.storage.mapper.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An audio track for a song. Can be a single-voice track or a mix.
 * Persisted in the Tracks table.
 * Tracks have a two-phase lifecycle: first, a track row is created with the metadata.
 * Next, the audio is processed and the blobName is set.
 * 
 * TODO Add ETag field to handle optimistic locking.
 */
@Data
@EqualsAndHashCode(callSuper = false) // ignore timestamp and ETag
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AudioTrackDTO extends BaseDTO {
    /** Partition key, same as partition key of Song. */
    @Getter(onMethod=@__(@PartitionKey))
    String songId;
    @Getter(onMethod=@__(@RowKey))
    String id;
    /**
     * The audio mix used to create this track. This will typically be a PartMix for single-part tracks,
     * or a StereoMix for multi-part tracks.
     * Persisted as JSON, not as reference to the PartsAndMixes table, because it has to match the mix used
     * <em>at the time the track was procesed</em>.
     */
    @Getter(onMethod = @__(@Property(type="json")))
    List<String> parts;
    @Getter(onMethod = @__(@Property(type="json")))
    AudioMix audioMix;
    Integer durationSec;
    /** When the track was created (metadata only) */
    Instant created;
    /** When the track was processed and the audio blob created, or null if not processed yet. */
    @Getter(onMethod = @__(@Timestamp))
    Instant updated;
    /** The name of the blob in the "song-media" blob container. */
    String blobName;

    @Transient
    public boolean isPartTrack() { return parts == null && audioMix == null; }
    @Transient
    public boolean isMixTrack() { return CollectionUtils.isNotEmpty(parts) && audioMix != null; }
    @Transient
    public boolean isValid() { return isPartTrack() || isMixTrack(); }
    @Transient
    public boolean hasMedia() { return getBlobName() != null; }

    public static AudioTrackDTOBuilder fromAudioTrack(AudioTrack track) {
        return AudioTrackDTO.builder()
            .songId(track.songId().value())
            .id(track.trackId())
            .blobName(null) // API track object doesn't store blob name
            .audioMix(null)
            .created(track.created())
            .updated(track.updated());
    }

    public static AudioTrackDTO fromPartTrack(PartTrack track) {
        return fromAudioTrack(track)
            .audioMix(null)
            .parts(null)
            .build();
    }

    public static AudioTrackDTO fromMixTrack(MixTrack track) {
        return fromAudioTrack(track)
            .audioMix(track.mix())
            .parts(track.parts().stream().map(AudioPart::name).toList())
            .build();
    }
}

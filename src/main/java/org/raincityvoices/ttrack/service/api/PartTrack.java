package org.raincityvoices.ttrack.service.api;

import java.net.URI;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@Accessors(fluent = true)
@Jacksonized // necessary because accessors aren't bean-like
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartTrack extends AudioTrack {

    private final AudioPart part;

    public String trackId() { return part.name(); }

    @Override
    public URI url() { return SongController.partTrackUrl(songId(), part()); }

    @Override
    public URI mediaUrl() { return hasMedia() ? SongController.partMediaUrl(songId(), part()) : null; }
}

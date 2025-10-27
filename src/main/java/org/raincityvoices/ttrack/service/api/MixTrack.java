package org.raincityvoices.ttrack.service.api;

import java.net.URI;
import java.util.List;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;

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
public class MixTrack extends AudioTrack {

    private final String name;
    private final List<AudioPart> parts;
    private final AudioMix mix;

    @Override
    public String trackId() { return name(); } 

    @Override
    public URI url() { return SongController.mixTrackUrl(songId(), name()); }

    @Override
    public URI mediaUrl() { return SongController.mixMediaUrl(songId(), name()); }
}

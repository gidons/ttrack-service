package org.raincityvoices.ttrack.service.api;

import java.net.URI;
import java.util.List;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.audio.model.AudioMix;
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
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Jacksonized
public class MixTrack extends AudioTrack {

    private final MixInfo mixInfo;

    @JsonIgnore
    public String name() { return mixInfo.name(); }
    @JsonIgnore
    public List<AudioPart> parts() { return mixInfo.parts(); }
    @JsonIgnore
    public AudioMix mix() { return mixInfo.mix(); }
    @Override
    public String trackId() { return name(); } 

    @Override
    public URI url() { return SongController.mixTrackUrl(songId(), name()); }

    @Override
    public URI mediaUrl() { return hasMedia() ? SongController.mixMediaUrl(songId(), name()) : null; }
}

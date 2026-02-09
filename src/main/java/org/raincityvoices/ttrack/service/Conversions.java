package org.raincityvoices.ttrack.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.api.MixTrack;
import org.raincityvoices.ttrack.service.api.PartTrack;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.api.TimedTextData;
import org.raincityvoices.ttrack.service.api.TimedTextData.DataType;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.timeddata.TimedTextDTO;

/**
 * Conversions between API and internal types.
 */
public class Conversions {

    public static String[] toPartNames(AudioPart[] parts) {
        return Stream.of(parts).map(AudioPart::name).toArray(String[]::new);
    }
    public static AudioPart[] toAudioParts(String[] partNames) {
        return Stream.of(partNames).map(AudioPart::new).toArray(AudioPart[]::new);
    }
    public static List<String> toPartNames(Collection<AudioPart> parts) {
        return parts.stream().map(AudioPart::name).toList();
    }
    public static List<AudioPart> toAudioParts(Collection<String> partNames) {
        return partNames.stream().map(AudioPart::new).toList();
    }

    public static MixTrack toMixTrack(AudioTrackDTO dto, MediaUrlProvider urlProvider) {
        assert dto.isMixTrack();
        return MixTrack.builder()
            .songId(new SongId(dto.getSongId()))
            .mixInfo(MixInfo.builder()
            .name(dto.getId())
            .parts(dto.getParts().stream().map(AudioPart::new).toList())
            .mix(dto.getAudioMix())
            .pitchShift(Objects.requireNonNullElse(dto.getPitchShift(), 0))
            .speedFactor(Objects.requireNonNullElse(dto.getSpeedFactor(), 1.0))
            .build())
            .created(dto.getCreated())
            .updated(dto.getUpdated())
            .currentTaskId(dto.getCurrentTaskId())
            .hasMedia(dto.hasMedia())
            .mediaUrl(urlProvider.getMediaUrl(dto))
            .durationSec(dto.getDurationSec())
            .build();
    }

    public static PartTrack toPartTrack(AudioTrackDTO dto, MediaUrlProvider urlProvider) {
        assert dto.isPartTrack();
        return PartTrack.builder()
            .songId(new SongId(dto.getSongId()))
            .part(new AudioPart(dto.getId()))
            .created(dto.getCreated())
            .updated(dto.getUpdated())
            .currentTaskId(dto.getCurrentTaskId())
            .hasMedia(dto.hasMedia())
            .mediaUrl(urlProvider.getMediaUrl(dto))
            .durationSec(dto.getDurationSec())
            .build();
    }

    public static TimedTextData toTimedTextData(List<TimedTextDTO> dtos) {
        Map<AudioPart, Map<DataType, List<TimedTextData.Entry>>> map = dtos.stream()
            .collect(groupingBy(dto -> new AudioPart(dto.part()), 
                toMap(dto -> DataType.of(dto.type()), 
                    dto -> dto.entries().stream()
                            .map(e -> new TimedTextData.Entry(e.t(), e.v()))
                            .toList()
                        )
                    )
                );
        return TimedTextData.builder()
            .byPart(map)
            .build();
    }

    public static List<TimedTextData.Entry> toTimedTextEntries(TimedTextDTO dto) {
        return dto.entries().stream().map(e -> new TimedTextData.Entry(e.t(), e.v())).toList();
    }

    static List<TimedTextDTO> fromTimedTextData(TimedTextData data) {
        return data.byPart().entrySet().stream().flatMap(pe -> 
            pe.getValue().entrySet().stream().map(te -> 
                fromTimedTextEntries(pe.getKey(), te.getKey(), te.getValue())
            )
        ).toList();
    }

    static TimedTextDTO fromTimedTextEntries(AudioPart part, DataType type, List<TimedTextData.Entry> entries) {
        return TimedTextDTO.builder()
            .part(part.name())
            .type(type.value())
            .entries(entries.stream().map(e -> new TimedTextDTO.Entry(e.t(), e.v())).toList())
            .build();
    }
}

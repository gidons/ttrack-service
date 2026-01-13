package org.raincityvoices.ttrack.service;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.api.MixTrack;
import org.raincityvoices.ttrack.service.api.PartTrack;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.api.TimedTextData;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.TimedTextDTO;

import com.azure.cosmos.implementation.guava25.collect.ImmutableMap;
import com.azure.cosmos.implementation.guava25.collect.ImmutableMap.Builder;

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
        Map<String, List<TimedTextData.Entry>> map = dtos.stream()
            .collect(toImmutableMap(
                dto -> dto.type(),
                (dto) -> {
                    AudioPart[] parts = Stream.of(dto.parts()).map(AudioPart::new).toList().toArray(new AudioPart[0]);
                    return dto.entries().stream()
                        .map(e -> toTimedTextEntry(e, parts))
                        .collect(toImmutableList());
                }
            ));
        return TimedTextData.builder()
            .entriesByType(map)
            .build();
    }

    private static TimedTextData.Entry toTimedTextEntry(TimedTextDTO.Entry e, AudioPart[] parts) {
        Builder<AudioPart, String> text = ImmutableMap.<AudioPart,String>builder();
        for (int i = 0; i < parts.length; ++i) {
            String value = e.v(i);
            if (value != null) { text.put(parts[i], value); }
        }
        return TimedTextData.Entry.builder()
            .timeMs(e.t())
            .text(text.build())
            .build();
    }

    static List<TimedTextDTO> fromTimedTextData(TimedTextData data) {
        return data.entriesByType().entrySet().stream().map(e -> fromTimedTextDataType(e.getKey(), e.getValue())).toList();
    }

    private static TimedTextDTO fromTimedTextDataType(String type, List<TimedTextData.Entry> entries) {
        Set<AudioPart> partSet = entries.stream()
            .flatMap(e -> e.text().keySet().stream())
            .collect(Collectors.toSet());
        partSet.remove(AudioPart.ALL);
        partSet.remove(AudioPart.NONE);
        AudioPart[] parts = partSet.toArray(new AudioPart[0]);

        return TimedTextDTO.builder()
            .type(type)
            .parts(toPartNames(parts))
            .entries(entries.stream().map(e -> fromTimedTextDataEntry(e, parts)).collect(toImmutableList()))
            .build();
    }

    private static TimedTextDTO.Entry fromTimedTextDataEntry(TimedTextData.Entry e, AudioPart[] parts) {
        String allText = e.text().get(AudioPart.ALL);
        String[] partTexts = null;
        if (allText == null) {
            partTexts = new String[parts.length];
            for (int i = 0; i < parts.length; ++i) {
                partTexts[i] = e.text().get(parts[i]);
            }
        }
        return TimedTextDTO.Entry.builder()
            .t(e.timeMs())
            .u(allText)
            .p(partTexts)
            .build();
    }

}

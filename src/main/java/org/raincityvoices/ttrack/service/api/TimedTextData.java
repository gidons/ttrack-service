package org.raincityvoices.ttrack.service.api;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.util.StringId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Builder
@Accessors(fluent = true)
public class TimedTextData {

    public static class DataType extends StringId {
        public DataType(String id) { super(id); }
        @JsonCreator
        public static DataType of(String id) { return new DataType(id); }
    }

    /** Song lyrics: text is the lyrics sung by each part. */
    public static final DataType TYPE_LYRICS = DataType.of("LYRICS");
    /** Song lyrics: text is the description of the bookmark, e.g. "Tag". */
    public static final DataType TYPE_BOOKMARKS = DataType.of("BOOKMARKS");
    /** Measure starts: text is the measure number, e.g. "13". Note that these might not be unique (repeats). */
    public static final DataType TYPE_MEASURES = DataType.of("MEASURES");

    @Value
    // @Builder
    @Accessors(fluent = true)
    @Getter(onMethod=@__(@JsonProperty()))
    public static class Entry {
        /** Time, in milliseconds since the beginning of the audio. */
        long t;
        /** Text value. May be empty but not null. */
        String v;
    }

    @JsonCreator
    public TimedTextData(Map<AudioPart, Map<DataType, List<Entry>>> entriesByType) {
        // Sort entries by time
        this.byPart = entriesByType.entrySet().stream().collect(toImmutableMap(te -> te.getKey(), 
            te -> te.getValue().entrySet().stream().collect(toImmutableMap(pe -> pe.getKey(), 
                pe -> pe.getValue()
                    .stream()
                    .sorted(Comparator.comparingLong(Entry::t))
                    .collect(toImmutableList())
                )
            )
        ));
    }

    @Getter(onMethod = @__(@JsonValue))
    Map<AudioPart, Map<DataType, List<Entry>>> byPart;
}

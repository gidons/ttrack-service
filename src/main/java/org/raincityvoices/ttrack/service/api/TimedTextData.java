package org.raincityvoices.ttrack.service.api;

import java.util.List;
import java.util.Map;

import org.raincityvoices.ttrack.service.audio.model.AudioPart;

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

    /** Song lyrics: text is the lyrics sung by each part. */
    public static final String TYPE_LYRICS = "LYRICS";
    /** Song lyrics: text is the description of the bookmark, e.g. "Tag". */
    public static final String TYPE_BOOKMARKS = "BOOKMARKS";
    /** Measure starts: text is the measure number, e.g. "13". Note that these might not be unique (repeats). */
    public static final String TYPE_MEASURES = "MEASURES";
    /** Key note frequency in Hz, e.g. "435" for a slightly flat A. */
    public static final String KEY_FREQUENCY = "KEYFREQ";

    @Value
    @Builder
    @Accessors(fluent = true)
    @Getter(onMethod=@__(@JsonProperty()))
    public static class Entry {
        long timeMs;
        /** The text for each part. Use AudioPart.ALL to indicate that the text applies to all parts. */
        Map<AudioPart, String> text;
    }

    @JsonCreator
    public TimedTextData(Map<String, List<Entry>> entriesByType) {
        this.entriesByType = entriesByType;
    }

    @Getter(onMethod = @__(@JsonValue))
    Map<String, List<Entry>> entriesByType;
}

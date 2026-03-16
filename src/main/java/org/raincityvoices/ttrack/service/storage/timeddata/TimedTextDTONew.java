package org.raincityvoices.ttrack.service.storage.timeddata;

import java.util.List;

import org.raincityvoices.ttrack.service.api.TimedTextData;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Builder
@Accessors(fluent = true)
@Getter(onMethod=@__(@JsonProperty()))
public class TimedTextDTONew {

    @Value
    @Builder
    @Accessors(fluent = true)
    @Getter(onMethod=@__(@JsonProperty()))
    public static class Entry {
        /** Time after song start, in milliseconds. */
        long t;
        /** Text content (value) */
        String v;
    }

    /** 
     * The type of information represented by this data.
     * This SHOULD be one of the constants TimedTextData.TYPE_XXX for the relevant data, e.g.
     * for lyrics data, use {@link TimedTextData#TYPE_LYRICS}. It MAY be a different string
     * for data that isn't covered by those constants, e.g. "Dynamics", in which case it may
     * be shown to the user as-is.
     */
    String type;
    /**
     * The audio part this data applies to.
     */
    String part;
    /**
     * The data itself.
     */
    List<Entry> entries;
}

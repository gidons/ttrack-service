package org.raincityvoices.ttrack.service.storage;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.List;

import org.raincityvoices.ttrack.service.api.TimedTextData;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Value
@Builder
@Accessors(fluent = true)
@Getter(onMethod=@__(@JsonProperty()))
public class TimedTextDTO {

    @Value
    @Builder
    @Accessors(fluent = true)
    @Getter(onMethod=@__(@JsonProperty()))
    @JsonInclude(Include.NON_NULL)
    public static class Entry {
        /** Time after song start, in milliseconds. */
        long t;
        /** Text, when it applies to all parts (aka unison). */
        String u;
        /** Per-part text, in the order specified in {@link TimedTextDTO#parts}, or null if unison. */
        String[] p;

        /** @return the text value for the ith part in the order of the containing {@link TimedTextDTO#parts()}. */
        public String v(int i) {
            return u != null ? u : p[i];
        }

        public static class EntryBuilder {
            public EntryBuilder p(String ... p) {
                this.p = p;
                return this;
            }
        }
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
     * The audio parts this data applies to, in order.
     */
    String[] parts;
    /**
     * The data itself.
     */
    List<Entry> entries;
}

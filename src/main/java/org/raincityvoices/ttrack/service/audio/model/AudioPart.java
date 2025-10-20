package org.raincityvoices.ttrack.service.audio.model;

import org.raincityvoices.ttrack.service.util.StringId;

import lombok.NoArgsConstructor;

/**
 * An identifier for a single song part, e.g. Soprano, Alto, Tenor, Bass.
 * Each part should correspond to a mono audio channel.
 * 
 * Note: Identity (equals/hashCode) is based on name only.
 */
@NoArgsConstructor
public class AudioPart extends StringId {

    public static AudioPart NONE = new AudioPart();

    public AudioPart(String value) {
        super(value);
    }
    /** Convenient alias for value(). */
    public String name() { return value(); }
}

package org.raincityvoices.ttrack.service.api;

import org.raincityvoices.ttrack.service.util.StringId;

import com.fasterxml.jackson.annotation.JsonCreator;

public class SongId extends StringId {

    public static final SongId NONE = new SongId();

    private SongId() {}

    public SongId(String value) { super(value); }

    @JsonCreator
    public static SongId orNone(String value) {
        return isValidId(value) ? new SongId(value) : NONE;
    }
}

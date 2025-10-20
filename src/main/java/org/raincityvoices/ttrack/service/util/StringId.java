package org.raincityvoices.ttrack.service.util;

import org.apache.commons.lang3.StringUtils;

import com.azure.cosmos.implementation.guava25.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonValue;

public abstract class StringId {
    private final String value;

    public StringId(String value) {
        Preconditions.checkArgument(isValidId(value), "ID values may not be null or blank.");
        this.value = value;
    }

    public static boolean isValidId(String value) { return StringUtils.isNotBlank(value); }

    protected StringId() {
        value = "";
    }

    @JsonValue
    public final String value() { return value; }

    @Override
    public boolean equals(Object obj) {
        return obj != null && this.getClass().equals(obj.getClass()) && value.equals(((StringId)obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + value;
    }
}

package org.raincityvoices.ttrack.service.storage.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;

public class DateHelper {

    private static final ZoneId UTC = ZoneId.of("UTC");
    public static final Set<Class<?>> SUPPORTED_TYPES = Set.of(OffsetDateTime.class, Instant.class, Date.class);

    public static Object convertFromOffsetDateTime(Object value, Class<?> targetType) {
        assert value instanceof OffsetDateTime;
        OffsetDateTime dtValue = (OffsetDateTime) value;
        if (targetType == OffsetDateTime.class) {
            return value;
        } else if (targetType == Instant.class) {
            return dtValue.toInstant();
        } else if (targetType == Date.class) {
            return Date.from(dtValue.toInstant());
        } else {
            throw new IllegalArgumentException("Property with type " + targetType + " does not support OffsetDateTime values.");
        }
    }

    public static OffsetDateTime convertToOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime) { 
            return (OffsetDateTime) value;
        } else if (value instanceof Instant) {
            return OffsetDateTime.ofInstant((Instant)value, UTC);
        } else if (value instanceof Date) {
            return OffsetDateTime.ofInstant(((Date)value).toInstant(), UTC);
        } else {
            throw new IllegalArgumentException("Value with type " + value.getClass() + " can not be converted to OffsetDateTime.");
        }
    }
}

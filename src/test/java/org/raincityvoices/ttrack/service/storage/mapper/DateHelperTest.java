package org.raincityvoices.ttrack.service.storage.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;

public class DateHelperTest {

    // NOTE: have to base all values on Date, because it's millisecond granularity whereas Instant and ODT are microsecond.
    // When we start with Instant, it's a non-whole number of ms, and then the Date is not equal to it.
    private static final Date NOW_DATE = new Date();
    private static final Instant NOW = NOW_DATE.toInstant();
    private static final OffsetDateTime NOW_ODT_UTC = OffsetDateTime.ofInstant(NOW, ZoneId.of("UTC"));
    private static final OffsetDateTime NOW_ODT_IST = OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Kolkata"));

    @Test
    void GIVEN_odt_in_UTC_WHEN_convertFromOffsetDateTime_with_target_Date_THEN_returns_correct_Date() {
        Date actual = (Date) DateHelper.convertFromOffsetDateTime(NOW_ODT_UTC, Date.class);
        assertEquals(NOW_DATE, actual);
    }

    @Test
    void GIVEN_odt_in_random_zone_WHEN_convertFromOffsetDateTime_with_target_Date_THEN_returns_correct_Date() {
        Date actual = (Date) DateHelper.convertFromOffsetDateTime(NOW_ODT_IST, Date.class);
        assertEquals(NOW_DATE, actual);
    }

    @Test
    void GIVEN_odt_in_random_zone_WHEN_convertFromOffsetDateTime_with_target_Instant_THEN_returns_correct_Instant() {
        Instant actual = (Instant) DateHelper.convertFromOffsetDateTime(NOW_ODT_IST, Instant.class);
        assertEquals(NOW, actual);
    }

    @Test
    void GIVEN_date_WHEN_convertToOffsetDateTime_THEN_returns_correct_ODT_in_UTC() {
        assertEquals(NOW_ODT_UTC, DateHelper.convertToOffsetDateTime(NOW_DATE));
    }

    @Test
    void GIVEN_instant_WHEN_convertToOffsetDateTime_THEN_returns_correct_ODT_in_UTC() {
        assertEquals(NOW_ODT_UTC, DateHelper.convertToOffsetDateTime(NOW));
    }
}

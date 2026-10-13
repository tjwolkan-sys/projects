package com.powerdialer.util;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

/**
 * Unit tests for timezone handling.
 * Verifies Istanbul timezone configuration and calculations.
 */
public class TimeZoneTest {

    @Test
    public void testIstanbulZoneIsCorrect() {
        assertEquals("Istanbul zone should be Europe/Istanbul",
                     "Europe/Istanbul",
                     Constants.ISTANBUL_ZONE.getId());
    }

    @Test
    public void testIstanbulZoneIsNotNull() {
        assertNotNull("Istanbul zone constant should not be null",
                      Constants.ISTANBUL_ZONE);
    }

    @Test
    public void testGetCurrentIstanbulTime() {
        LocalDateTime istanbulTime = LocalDateTime.now(Constants.ISTANBUL_ZONE);

        assertNotNull("Istanbul time should not be null", istanbulTime);
        assertTrue("Istanbul time should be a valid datetime",
                   istanbulTime.getYear() > 2020);
    }

    @Test
    public void testIstanbulTimeVsSystemTime() {
        LocalDateTime istanbulTime = LocalDateTime.now(Constants.ISTANBUL_ZONE);
        LocalDateTime systemTime = LocalDateTime.now();

        assertNotNull("System time should not be null", systemTime);
        assertNotNull("Istanbul time should not be null", istanbulTime);

        // Both should be valid current times
        assertTrue("System time should be a valid datetime",
                   systemTime.getYear() > 2020);
        assertTrue("Istanbul time should be a valid datetime",
                   istanbulTime.getYear() > 2020);
    }

    @Test
    public void testIstanbulTimeConsistency() {
        LocalDateTime istanbulTime1 = LocalDateTime.now(Constants.ISTANBUL_ZONE);
        LocalDateTime istanbulTime2 = LocalDateTime.now(Constants.ISTANBUL_ZONE);

        // Times should be very close (within a few seconds)
        long secondsDiff = Math.abs(
            istanbulTime1.atZone(Constants.ISTANBUL_ZONE).toEpochSecond() -
            istanbulTime2.atZone(Constants.ISTANBUL_ZONE).toEpochSecond()
        );

        assertTrue("Istanbul times should be within 5 seconds of each other",
                   secondsDiff < 5);
    }

    @Test
    public void testSystemTimeConsistency() {
        LocalDateTime systemTime1 = LocalDateTime.now();
        LocalDateTime systemTime2 = LocalDateTime.now();

        // Times should be very close (within a few seconds)
        long secondsDiff = Math.abs(
            systemTime1.atZone(ZoneId.systemDefault()).toEpochSecond() -
            systemTime2.atZone(ZoneId.systemDefault()).toEpochSecond()
        );

        assertTrue("System times should be within 5 seconds of each other",
                   secondsDiff < 5);
    }

    @Test
    public void testTimeZoneConversion() {
        // Create a specific time in Istanbul
        ZonedDateTime istanbulTime = ZonedDateTime.of(
            2025, 12, 25, 15, 30, 0, 0,
            Constants.ISTANBUL_ZONE
        );

        // Convert to UTC
        ZonedDateTime utcTime = istanbulTime.withZoneSameInstant(ZoneId.of("UTC"));

        // Istanbul is UTC+3, so UTC should be 3 hours earlier
        assertEquals("UTC hour should be 3 hours less than Istanbul",
                     12, utcTime.getHour());
        assertEquals("Day should be the same",
                     istanbulTime.getDayOfMonth(), utcTime.getDayOfMonth());
    }

    @Test
    public void testIstanbulZoneOffset() {
        ZonedDateTime istanbulTime = ZonedDateTime.now(Constants.ISTANBUL_ZONE);

        // Istanbul is UTC+3 (standard time, no DST since 2016)
        int offsetHours = istanbulTime.getOffset().getTotalSeconds() / 3600;

        assertEquals("Istanbul should be UTC+3", 3, offsetHours);
    }

    @Test
    public void testSameInstantDifferentZones() {
        // Get current moment in Istanbul time
        ZonedDateTime istanbulTime = ZonedDateTime.now(Constants.ISTANBUL_ZONE);

        // Convert same instant to system timezone
        ZonedDateTime systemTime = istanbulTime.withZoneSameInstant(ZoneId.systemDefault());

        // They should represent the exact same moment
        assertEquals("Both times should represent the same instant",
                     istanbulTime.toInstant(),
                     systemTime.toInstant());
    }

    @Test
    public void testLocalDateTimeNowInIstanbul() {
        // This is how the application gets Istanbul time
        LocalDateTime applicationNow = LocalDateTime.now(Constants.ISTANBUL_ZONE);

        assertNotNull("Istanbul LocalDateTime should not be null", applicationNow);

        // Verify it's a reasonable current time
        int currentYear = java.time.Year.now().getValue();
        assertEquals("Year should be current year", currentYear, applicationNow.getYear());
    }

    @Test
    public void testZoneIdIsSerializable() {
        // Verify that the zone ID can be used in serialization contexts
        String zoneId = Constants.ISTANBUL_ZONE.getId();
        ZoneId reconstructed = ZoneId.of(zoneId);

        assertEquals("Reconstructed zone should equal original",
                     Constants.ISTANBUL_ZONE,
                     reconstructed);
    }
}


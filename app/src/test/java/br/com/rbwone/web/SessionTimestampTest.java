package br.com.rbwone.web;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class SessionTimestampTest {
    private SessionRecord record(String expiry) {
        return new SessionRecord("a".repeat(64), true, "a1c3ad09-79b6-481e-b496-e88ed28e2209", "f1c3ad09-79b6-481e-b496-e88ed28e2210", expiry, "fcm-device-token");
    }
    private final long now = Instant.parse("2026-09-24T20:00:00Z").toEpochMilli();
    @Test public void acceptsActualPostgresTimestampWithOffsetAndMicroseconds() {
        assertTrue(record("2026-09-25T20:05:37.103332+00:00").verified(now));
    }
    @Test public void utcAndBrazilianOffsetsRepresentSameExpiry() {
        for (String expiry : new String[]{"2026-09-24T20:00:01Z", "2026-09-24T20:00:01+00:00", "2026-09-24T17:00:01-03:00"}) {
            assertTrue(record(expiry).verified(now)); assertFalse(record(expiry).verified(now + 1000));
        }
    }
    @Test public void rejectsMissingOffsetInvalidDateAndExpiredSession() {
        for (String expiry : new String[]{"", "2026-09-25T20:00:00", "2026-02-30T20:00:00Z", "2026-09-24T20:00:00+00:00", "bad"}) assertFalse(expiry, record(expiry).verified(now));
    }
}

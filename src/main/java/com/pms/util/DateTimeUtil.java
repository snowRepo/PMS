package com.pms.util;

import java.time.Instant;

/**
 * Provides UTC timestamps in ISO-8601 format for all database records.
 * All timestamps stored as TEXT in SQLite and TIMESTAMPTZ in PostgreSQL.
 */
public class DateTimeUtil {

    private DateTimeUtil() {}

    /** Returns current UTC time as ISO-8601 string e.g. "2025-01-15T10:30:00Z" */
    public static String now() {
        return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
    }

    /**
     * Cleans up ISO-8601 strings for UI display, stripping fractional seconds.
     * Example: "2025-01-15T10:30:00.123456Z" -> "2025-01-15 10:30:00"
     */
    public static String formatForDisplay(String isoString) {
        if (isoString == null) return "";
        // Replace the 'T' with a space and strip everything after the dot or 'Z'
        String clean = isoString.replace("T", " ").replace("Z", "");
        int dotIndex = clean.indexOf('.');
        if (dotIndex != -1) {
            clean = clean.substring(0, dotIndex);
        }
        return clean;
    }
}

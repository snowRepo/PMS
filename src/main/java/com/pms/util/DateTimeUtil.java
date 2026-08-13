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
        return Instant.now().toString();
    }
}

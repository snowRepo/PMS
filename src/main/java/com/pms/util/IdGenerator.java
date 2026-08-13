package com.pms.util;

import java.util.UUID;

/**
 * Generates UUIDs used as primary keys for all records.
 * Using UUIDs instead of auto-increment ensures no key collisions
 * when multiple offline clients eventually sync to the cloud.
 */
public class IdGenerator {

    private IdGenerator() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}

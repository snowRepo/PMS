package com.pms.util;

import com.pms.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Simple key-value store backed by the local SQLite app_prefs table.
 * Used for persisting configuration across launches (DB credentials, setup state, etc.).
 */
public final class AppPrefs {

    private static final Logger logger = LoggerFactory.getLogger(AppPrefs.class);

    private AppPrefs() {}

    /**
     * Returns the value for a key, or {@code defaultValue} if the key doesn't exist.
     */
    public static String get(String key, String defaultValue) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT value FROM app_prefs WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : defaultValue;
            }
        } catch (SQLException e) {
            logger.warn("AppPrefs.get failed for key '{}': {}", key, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Saves a key-value pair, replacing any existing value.
     */
    public static void set(String key, String value) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR REPLACE INTO app_prefs (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("AppPrefs.set failed for key '{}': {}", key, e.getMessage());
        }
    }

    /** Removes a preference key. */
    public static void remove(String key) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM app_prefs WHERE key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("AppPrefs.remove failed for key '{}': {}", key, e.getMessage());
        }
    }

    // ── Typed convenience getters ─────────────────────────────────────────────

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }
}

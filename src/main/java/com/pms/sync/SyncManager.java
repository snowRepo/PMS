package com.pms.sync;

import com.pms.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SyncManager — handles bidirectional sync between local SQLite and cloud PostgreSQL.
 *
 * Strategy:
 *  - Every record has a `synced` flag (0 = pending, 1 = synced).
 *  - Every write to local also writes a row to sync_log.
 *  - SyncManager reads sync_log and pushes unsynced records to the cloud.
 *  - Pull: fetches records updated on cloud after last_sync_time and upserts locally.
 *
 * Auto-sync runs every 30 seconds when the cloud is reachable.
 */
public class SyncManager {

    private static final Logger logger = LoggerFactory.getLogger(SyncManager.class);
    private static final SyncManager INSTANCE = new SyncManager();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PharmSys-Sync");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;

    private SyncManager() {}

    public static SyncManager getInstance() {
        return INSTANCE;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Starts the auto-sync background thread.
     * First tries to connect to the cloud; if that fails, keeps retrying every 30s.
     */
    public void startAutoSync() {
        if (running) return;
        running = true;

        logger.info("Auto-sync scheduler started (interval: 30s).");
        scheduler.scheduleWithFixedDelay(this::syncCycle, 0, 30, TimeUnit.SECONDS);
    }

    /** Trigger a manual one-shot sync (e.g. from a UI button). */
    public void triggerManualSync() {
        if (!running) return;
        scheduler.submit(this::syncCycle);
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        logger.info("Sync scheduler shut down.");
    }

    public boolean isCloudAvailable() {
        return DatabaseConfig.isCloudAvailable();
    }

    // ─── Sync cycle ───────────────────────────────────────────────────────────

    private void syncCycle() {
        // Try to establish cloud connection if not already available
        if (!DatabaseConfig.isCloudAvailable()) {
            logger.debug("Attempting cloud reconnect...");
            DatabaseConfig.initRemote();
        }

        if (!DatabaseConfig.isCloudAvailable()) {
            logger.debug("Cloud unavailable — skipping sync cycle.");
            return;
        }

        try {
            pushLocalChanges();
            pullRemoteChanges();
            logger.info("Sync cycle completed at {}", Instant.now());
        } catch (Exception e) {
            logger.error("Sync cycle failed", e);
        }
    }

    // ─── Push: local → cloud ──────────────────────────────────────────────────

    /**
     * Reads all unsynced entries from sync_log and pushes them to the cloud.
     * Tables currently supported: products, sales, sale_items, customers, suppliers, purchases, purchase_items, users
     */
    private void pushLocalChanges() throws SQLException {
        Connection local = DatabaseConfig.getLocalConnection();
        Connection cloud = DatabaseConfig.getRemoteConnection();

        if (cloud == null) return;

        try (cloud) {
            cloud.setAutoCommit(false);

            String query = "SELECT id, table_name, record_id, operation FROM sync_log WHERE synced = 0 ORDER BY id";
            try (PreparedStatement ps = local.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    long logId      = rs.getLong("id");
                    String table    = rs.getString("table_name");
                    String recordId = rs.getString("record_id");
                    String op       = rs.getString("operation");

                    try {
                        if ("DELETE".equals(op)) {
                            softDeleteOnCloud(cloud, table, recordId);
                        } else {
                            upsertToCloud(local, cloud, table, recordId);
                        }

                        markSyncLogDone(local, logId);
                    } catch (Exception e) {
                        logger.warn("Failed to sync {} {} {}: {}", op, table, recordId, e.getMessage());
                    }
                }
            }

            cloud.commit();
        } catch (SQLException e) {
            cloud.rollback();
            throw e;
        }
    }

    private void upsertToCloud(Connection local, Connection cloud, String table, String recordId) throws SQLException {
        // Generic fetch from local by primary key
        String select = "SELECT * FROM " + table + " WHERE id = ?";
        try (PreparedStatement ps = local.prepareStatement(select)) {
            ps.setString(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return; // Record may have been deleted locally

                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();

                // Build INSERT ... ON CONFLICT (id) DO UPDATE for PostgreSQL
                StringBuilder columns = new StringBuilder();
                StringBuilder placeholders = new StringBuilder();
                StringBuilder updates = new StringBuilder();
                Object[] values = new Object[cols];

                for (int i = 1; i <= cols; i++) {
                    String col = meta.getColumnName(i);
                    values[i - 1] = rs.getObject(i);
                    if (i > 1) { columns.append(", "); placeholders.append(", "); }
                    columns.append(col);
                    placeholders.append("?");
                    if (!"id".equals(col)) {
                        if (updates.length() > 0) updates.append(", ");
                        updates.append(col).append(" = EXCLUDED.").append(col);
                    }
                }

                String upsert = String.format(
                    "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (id) DO UPDATE SET %s",
                    table, columns, placeholders, updates
                );

                try (PreparedStatement upsertPs = cloud.prepareStatement(upsert)) {
                    for (int i = 0; i < values.length; i++) {
                        upsertPs.setObject(i + 1, values[i]);
                    }
                    upsertPs.executeUpdate();
                }
            }
        }
    }

    private void softDeleteOnCloud(Connection cloud, String table, String recordId) throws SQLException {
        // For tables with an `active` column, soft delete; otherwise hard delete
        String sql = "UPDATE " + table + " SET active = 0 WHERE id = ?";
        try (PreparedStatement ps = cloud.prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.executeUpdate();
        }
    }

    private void markSyncLogDone(Connection local, long logId) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("UPDATE sync_log SET synced = 1 WHERE id = ?")) {
            ps.setLong(1, logId);
            ps.executeUpdate();
        }
    }

    // ─── Pull: cloud → local ──────────────────────────────────────────────────

    /**
     * Pulls records updated on the cloud since the last successful sync
     * and upserts them into the local SQLite database.
     *
     * NOTE: Tables must have an `updated_at` TEXT column for this to work.
     */
    private void pullRemoteChanges() throws SQLException {
        Connection local  = DatabaseConfig.getLocalConnection();
        Connection cloud  = DatabaseConfig.getRemoteConnection();
        if (cloud == null) return;

        String[] tables = {"users", "products", "customers", "suppliers", "sales", "sale_items", "purchases", "purchase_items"};
        String lastSync = getLastSyncTimestamp(local);

        try (cloud) {
            for (String table : tables) {
                try {
                    pullTable(local, cloud, table, lastSync);
                } catch (Exception e) {
                    logger.warn("Pull failed for table {}: {}", table, e.getMessage());
                }
            }
        }

        saveLastSyncTimestamp(local, Instant.now().toString());
    }

    private void pullTable(Connection local, Connection cloud, String table, String since) throws SQLException {
        // Tables without updated_at (e.g. sale_items) are skipped for pull
        String query;
        if ("sale_items".equals(table) || "purchase_items".equals(table)) {
            query = "SELECT * FROM " + table + " WHERE synced = 0";
        } else {
            query = "SELECT * FROM " + table + " WHERE updated_at > '" + since + "'";
        }

        try (Statement st = cloud.createStatement();
             ResultSet rs = st.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            while (rs.next()) {
                StringBuilder columns = new StringBuilder();
                StringBuilder placeholders = new StringBuilder();
                Object[] values = new Object[cols];

                for (int i = 1; i <= cols; i++) {
                    values[i - 1] = rs.getObject(i);
                    if (i > 1) { columns.append(", "); placeholders.append(", "); }
                    columns.append(meta.getColumnName(i));
                    placeholders.append("?");
                }

                // SQLite upsert
                String upsert = String.format(
                    "INSERT OR REPLACE INTO %s (%s) VALUES (%s)",
                    table, columns, placeholders
                );

                try (PreparedStatement ps = local.prepareStatement(upsert)) {
                    for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
                    ps.executeUpdate();
                }
            }
        }
    }

    // ─── Sync metadata ────────────────────────────────────────────────────────

    private static final String PREFS_TABLE_DDL =
        "CREATE TABLE IF NOT EXISTS app_prefs (key TEXT PRIMARY KEY, value TEXT)";

    private String getLastSyncTimestamp(Connection local) throws SQLException {
        try (Statement st = local.createStatement()) {
            st.execute(PREFS_TABLE_DDL);
        }
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT value FROM app_prefs WHERE key = 'last_sync'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "1970-01-01T00:00:00Z";
            }
        }
    }

    private void saveLastSyncTimestamp(Connection local, String ts) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement(
                "INSERT OR REPLACE INTO app_prefs (key, value) VALUES ('last_sync', ?)")) {
            ps.setString(1, ts);
            ps.executeUpdate();
        }
    }
}

package com.pms.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.pms.util.AppPrefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Manages local SQLite and remote (PostgreSQL / MySQL / MariaDB) connections.
 *
 *  Local  → SQLite at database/pms_local.db  (always available)
 *  Remote → Connection pool built from credentials stored in app_prefs
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    // ─── Local SQLite ─────────────────────────────────────────────────────────
    private static final String LOCAL_JDBC = "jdbc:sqlite:database/pms_local.db";
    private static Connection localConnection;

    // ─── Remote pool ─────────────────────────────────────────────────────────
    private static HikariDataSource remotePool;
    private static boolean cloudAvailable = false;

    // ═══════════════════════════════════════════════════════════════════════════
    //  LOCAL
    // ═══════════════════════════════════════════════════════════════════════════

    public static void initLocal() {
        try {
            localConnection = DriverManager.getConnection(LOCAL_JDBC);
            logger.info("Local SQLite connected.");
            createLocalSchema();
            runMigrations();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open local database", e);
        }
    }

    public static Connection getLocalConnection() {
        return localConnection;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  REMOTE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Attempts to build a remote connection from credentials saved in app_prefs.
     * Safe to call when offline — failure is logged, not thrown.
     */
    public static boolean initRemote() {
        if (!AppPrefs.getBoolean("db_enabled", true)) {
            logger.info("Cloud database is disabled in settings.");
            cloudAvailable = false;
            return false;
        }

        String dbType = AppPrefs.get("db_type", "");
        String host   = AppPrefs.get("db_host", "");
        String port   = AppPrefs.get("db_port", "5432");
        String dbName = AppPrefs.get("db_name", "");
        String user   = AppPrefs.get("db_user", "");
        String pass   = AppPrefs.get("db_pass", "");
        boolean ssl   = AppPrefs.getBoolean("db_ssl", false);

        if (dbType.isBlank() || host.isBlank()) {
            logger.info("No remote DB configured — offline mode.");
            cloudAvailable = false;
            return false;
        }

        return connectRemote(dbType, host, port, dbName, user, pass, ssl);
    }

    /**
     * Connects (or reconnects) with the given credentials.
     * Closes any existing pool first.
     */
    public static boolean connectRemote(String type, String host, String port,
                                        String dbName, String user, String pass, boolean ssl) {
        // Close existing pool if open
        if (remotePool != null && !remotePool.isClosed()) {
            remotePool.close();
            cloudAvailable = false;
        }

        try {
            String url = DbConnectionBuilder.buildUrl(type, host, port, dbName, ssl);
            Class.forName(DbConnectionBuilder.driverClass(type));

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(6000);
            config.setValidationTimeout(3000);
            config.setPoolName("PMS-Cloud");

            remotePool = new HikariDataSource(config);
            cloudAvailable = true;
            logger.info("Remote DB connected: {}:{}/{}", host, port, dbName);

            // Ensure remote schema exists
            try (Connection conn = remotePool.getConnection()) {
                createRemoteSchema(conn, type);
            }
            return true;

        } catch (Exception e) {
            logger.warn("Remote DB unavailable — offline mode. ({})", e.getMessage());
            cloudAvailable = false;
            return false;
        }
    }

    /** Returns a remote connection from the pool, or null if unavailable. */
    public static Connection getRemoteConnection() throws SQLException {
        if (!cloudAvailable || remotePool == null) return null;
        return remotePool.getConnection();
    }

    public static boolean isCloudAvailable() { return cloudAvailable; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SHUTDOWN
    // ═══════════════════════════════════════════════════════════════════════════

    public static void closeAll() {
        try {
            if (localConnection != null && !localConnection.isClosed())
                localConnection.close();
        } catch (SQLException e) {
            logger.error("Error closing local connection", e);
        }
        if (remotePool != null && !remotePool.isClosed())
            remotePool.close();
        
        cloudAvailable = false;
        logger.info("All database connections closed.");
    }

    /**
     * Drops all local data tables (preserving app_prefs) and recreates the schema.
     * The result is a clean local database with no records.
     * @param keepUsers If true, the users table is preserved so offline login still works.
     */
    public static void resetLocalData(boolean keepUsers) throws SQLException {
        java.util.List<String> dropList = new java.util.ArrayList<>(java.util.Arrays.asList(
            "DROP TABLE IF EXISTS activity_logs",
            "DROP TABLE IF EXISTS sync_log",
            "DROP TABLE IF EXISTS sale_items",
            "DROP TABLE IF EXISTS sales",
            "DROP TABLE IF EXISTS purchase_items",
            "DROP TABLE IF EXISTS purchases",
            "DROP TABLE IF EXISTS products",
            "DROP TABLE IF EXISTS categories",
            "DROP TABLE IF EXISTS suppliers",
            "DROP TABLE IF EXISTS customers",
            "DROP TABLE IF EXISTS shifts"
        ));

        if (!keepUsers) {
            dropList.add("DROP TABLE IF EXISTS users");
        }
        // app_prefs is intentionally NOT dropped here

        try (Statement stmt = localConnection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF");
            for (String sql : dropList) stmt.execute(sql);
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        createLocalSchema();
        runMigrations();
        logger.info("Local database has been wiped and schema recreated.");
    }

    /**
     * Deletes all rows from every remote table without dropping the schema.
     * Safe to call while the remote pool is active.
     */
    public static void wipeRemoteData() throws SQLException {
        if (!cloudAvailable || remotePool == null) {
            throw new SQLException("No active cloud connection.");
        }

        String[] tables = {
            "activity_logs", "sync_log",
            "sale_items", "sales",
            "purchase_items", "purchases",
            "products", "categories",
            "suppliers", "customers",
            "shifts", "users", "app_prefs"
        };

        try (Connection conn = remotePool.getConnection();
             Statement stmt = conn.createStatement()) {
            // Disable FK checks for the session (works on PostgreSQL, MySQL, MariaDB)
            try { stmt.execute("SET session_replication_role = 'replica'"); } catch (SQLException ignored) {
                try { stmt.execute("SET FOREIGN_KEY_CHECKS=0"); } catch (SQLException ignored2) { /* MariaDB/MySQL */ }
            }
            for (String table : tables) {
                try {
                    stmt.execute("DELETE FROM " + table);
                } catch (SQLException e) {
                    logger.warn("Could not wipe remote table '{}': {}", table, e.getMessage());
                }
            }
            // Re-enable FK checks
            try { stmt.execute("SET session_replication_role = 'origin'"); } catch (SQLException ignored) {
                try { stmt.execute("SET FOREIGN_KEY_CHECKS=1"); } catch (SQLException ignored2) { /* MariaDB/MySQL */ }
            }
        }
        logger.info("Remote database data has been wiped.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LOCAL SCHEMA
    // ═══════════════════════════════════════════════════════════════════════════

    private static void createLocalSchema() throws SQLException {
        String[] ddl = {

            // App preferences (must be first — AppPrefs depends on it)
            """
            CREATE TABLE IF NOT EXISTS app_prefs (
                key   TEXT PRIMARY KEY,
                value TEXT
            )
            """,

            // Users Table
            """
            CREATE TABLE IF NOT EXISTS users (
                id                   TEXT PRIMARY KEY,
                username             TEXT NOT NULL UNIQUE,
                password             TEXT NOT NULL,
                role                 TEXT NOT NULL DEFAULT 'cashier',
                full_name            TEXT,
                active               INTEGER NOT NULL DEFAULT 1,
                pin_hash             TEXT,
                is_temp_password     INTEGER NOT NULL DEFAULT 0,
                last_password_change TEXT NOT NULL DEFAULT (datetime('now')),
                prev_password_hash   TEXT,
                created_at           TEXT NOT NULL,
                updated_at           TEXT NOT NULL,
                synced               INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Categories Table
            """
            CREATE TABLE IF NOT EXISTS categories (
                id              TEXT PRIMARY KEY,
                name            TEXT UNIQUE NOT NULL,
                description     TEXT,
                created_at      TEXT NOT NULL,
                updated_at      TEXT NOT NULL,
                synced          INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Products Table
            """
            CREATE TABLE IF NOT EXISTS products (
                id              TEXT PRIMARY KEY,
                name            TEXT NOT NULL,
                generic_name    TEXT,
                barcode         TEXT,
                category        TEXT,
                manufacturer    TEXT,
                unit            TEXT,
                cost_price      REAL NOT NULL DEFAULT 0,
                selling_price   REAL NOT NULL DEFAULT 0,
                stock_qty       INTEGER NOT NULL DEFAULT 0,
                reorder_level   INTEGER NOT NULL DEFAULT 10,
                expiry_date     TEXT,
                description     TEXT,
                active          INTEGER NOT NULL DEFAULT 1,
                created_at      TEXT NOT NULL,
                updated_at      TEXT NOT NULL,
                synced          INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Sales header
            """
            CREATE TABLE IF NOT EXISTS sales (
                id              TEXT PRIMARY KEY,
                sale_date       TEXT NOT NULL,
                total_amount    REAL NOT NULL DEFAULT 0,
                discount        REAL NOT NULL DEFAULT 0,
                tax             REAL NOT NULL DEFAULT 0,
                amount_paid     REAL NOT NULL DEFAULT 0,
                change_amount   REAL NOT NULL DEFAULT 0,
                payment_method  TEXT NOT NULL DEFAULT 'cash',
                payment_ref     TEXT,
                cashier_id      TEXT,
                customer_id     TEXT,
                customer_name   TEXT,
                notes           TEXT,
                created_at      TEXT NOT NULL,
                synced          INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Sale line items
            """
            CREATE TABLE IF NOT EXISTS sale_items (
                id          TEXT PRIMARY KEY,
                sale_id     TEXT NOT NULL,
                product_id  TEXT NOT NULL,
                qty         INTEGER NOT NULL,
                unit_price  REAL NOT NULL,
                cost_price  REAL NOT NULL DEFAULT 0.0,
                discount    REAL NOT NULL DEFAULT 0,
                subtotal    REAL NOT NULL,
                synced      INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (sale_id) REFERENCES sales(id),
                FOREIGN KEY (product_id) REFERENCES products(id)
            )
            """,

            // Suppliers
            """
            CREATE TABLE IF NOT EXISTS suppliers (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                contact     TEXT,
                phone       TEXT,
                email       TEXT,
                address     TEXT,
                active      INTEGER NOT NULL DEFAULT 1,
                created_at  TEXT NOT NULL,
                updated_at  TEXT NOT NULL,
                synced      INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Purchase orders
            """
            CREATE TABLE IF NOT EXISTS purchases (
                id              TEXT PRIMARY KEY,
                supplier_id     TEXT,
                purchase_date   TEXT NOT NULL,
                total_amount    REAL NOT NULL DEFAULT 0,
                status          TEXT NOT NULL DEFAULT 'pending',
                notes           TEXT,
                created_at      TEXT NOT NULL,
                synced          INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
            )
            """,

            // Purchase line items
            """
            CREATE TABLE IF NOT EXISTS purchase_items (
                id          TEXT PRIMARY KEY,
                purchase_id TEXT NOT NULL,
                product_id  TEXT NOT NULL,
                qty         INTEGER NOT NULL,
                unit_cost   REAL NOT NULL,
                subtotal    REAL NOT NULL,
                synced      INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (purchase_id) REFERENCES purchases(id),
                FOREIGN KEY (product_id) REFERENCES products(id)
            )
            """,

            // Customers
            """
            CREATE TABLE IF NOT EXISTS customers (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                phone       TEXT,
                email       TEXT,
                address     TEXT,
                active      INTEGER NOT NULL DEFAULT 1,
                created_at  TEXT NOT NULL,
                updated_at  TEXT NOT NULL,
                synced      INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Shifts
            """
            CREATE TABLE IF NOT EXISTS shifts (
                id                   TEXT PRIMARY KEY,
                cashier_id           TEXT NOT NULL,
                start_time           TEXT NOT NULL,
                end_time             TEXT,
                starting_cash        REAL NOT NULL,
                cash_sales           REAL NOT NULL DEFAULT 0,
                momo_sales           REAL NOT NULL DEFAULT 0,
                card_sales           REAL NOT NULL DEFAULT 0,
                expected_ending_cash REAL,
                declared_ending_cash REAL,
                discrepancy          REAL,
                discrepancy_resolved INTEGER NOT NULL DEFAULT 0,
                status               TEXT NOT NULL,
                notes                TEXT,
                synced               INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(cashier_id) REFERENCES users(id)
            )
            """,

            // Sync queue
            """
            CREATE TABLE IF NOT EXISTS sync_log (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name  TEXT NOT NULL,
                record_id   TEXT NOT NULL,
                operation   TEXT NOT NULL,
                created_at  TEXT NOT NULL,
                synced      INTEGER NOT NULL DEFAULT 0
            )
            """,

            // Activity Logs
            """
            CREATE TABLE IF NOT EXISTS activity_logs (
                id          TEXT PRIMARY KEY,
                user_id     TEXT,
                username    TEXT,
                action      TEXT NOT NULL,
                description TEXT NOT NULL,
                created_at  TEXT NOT NULL,
                synced      INTEGER NOT NULL DEFAULT 0
            )
            """
        };

        try (Statement stmt = localConnection.createStatement()) {
            for (String sql : ddl) stmt.execute(sql);
        }
        logger.info("Local schema verified.");
    }

    /**
     * Adds any missing columns to existing tables (safe for updates).
     * SQLite will throw if a column already exists — we catch and ignore those.
     */
    private static void runMigrations() {
        String[][] migrations = {
            { "users", "ALTER TABLE users ADD COLUMN pin_hash TEXT" },
            { "users", "ALTER TABLE users ADD COLUMN is_temp_password INTEGER NOT NULL DEFAULT 0" },
            { "users", "ALTER TABLE users ADD COLUMN last_password_change TEXT NOT NULL DEFAULT (datetime('now'))" },
            { "users", "ALTER TABLE users ADD COLUMN prev_password_hash TEXT" },
            { "sales", "ALTER TABLE sales ADD COLUMN payment_ref TEXT" },
            { "sales", "ALTER TABLE sales ADD COLUMN customer_id TEXT" },
            { "customers", "ALTER TABLE customers ADD COLUMN active INTEGER NOT NULL DEFAULT 1" },
            { "purchase_items", "ALTER TABLE purchase_items RENAME COLUMN medicine_id TO product_id" },
            { "shifts", "ALTER TABLE shifts ADD COLUMN discrepancy_resolved INTEGER NOT NULL DEFAULT 0" },
            { "sale_items", "ALTER TABLE sale_items ADD COLUMN cost_price REAL NOT NULL DEFAULT 0.0" },
            // activity_logs is handled by CREATE TABLE IF NOT EXISTS in createLocalSchema()
        };

        try (Statement stmt = localConnection.createStatement()) {
            for (String[] m : migrations) {
                try {
                    stmt.execute(m[1]);
                } catch (SQLException ignored) {
                    // Column already exists or rename fails - expected for fresh installs/already migrated
                }
            }
        } catch (SQLException e) {
            logger.error("Migration error", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  REMOTE SCHEMA  (PostgreSQL / MySQL / MariaDB — shared DDL)
    // ═══════════════════════════════════════════════════════════════════════════

    private static void createRemoteSchema(Connection conn, String dbType) throws SQLException {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS app_prefs (
                key   VARCHAR(255) PRIMARY KEY,
                value TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS users (
                id                   VARCHAR(36) PRIMARY KEY,
                username             VARCHAR(100) NOT NULL UNIQUE,
                password             TEXT NOT NULL,
                role                 VARCHAR(30) NOT NULL DEFAULT 'cashier',
                full_name            VARCHAR(200),
                active               SMALLINT NOT NULL DEFAULT 1,
                pin_hash             TEXT,
                is_temp_password     SMALLINT NOT NULL DEFAULT 0,
                last_password_change VARCHAR(30) NOT NULL,
                prev_password_hash   TEXT,
                created_at           VARCHAR(30) NOT NULL,
                updated_at           VARCHAR(30) NOT NULL,
                synced               SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS categories (
                id              VARCHAR(36) PRIMARY KEY,
                name            VARCHAR(255) NOT NULL UNIQUE,
                description     TEXT,
                created_at      VARCHAR(30) NOT NULL,
                updated_at      VARCHAR(30) NOT NULL,
                synced          SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS products (
                id              VARCHAR(36) PRIMARY KEY,
                name            VARCHAR(255) NOT NULL,
                generic_name    VARCHAR(255),
                barcode         VARCHAR(100),
                category        VARCHAR(100),
                manufacturer    VARCHAR(200),
                unit            VARCHAR(50),
                cost_price      DECIMAL(12,2) NOT NULL DEFAULT 0,
                selling_price   DECIMAL(12,2) NOT NULL DEFAULT 0,
                stock_qty       INT NOT NULL DEFAULT 0,
                reorder_level   INT NOT NULL DEFAULT 10,
                expiry_date     VARCHAR(20),
                description     TEXT,
                active          SMALLINT NOT NULL DEFAULT 1,
                created_at      VARCHAR(30) NOT NULL,
                updated_at      VARCHAR(30) NOT NULL,
                synced          SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sales (
                id              VARCHAR(36) PRIMARY KEY,
                sale_date       VARCHAR(30) NOT NULL,
                total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
                discount        DECIMAL(12,2) NOT NULL DEFAULT 0,
                tax             DECIMAL(12,2) NOT NULL DEFAULT 0,
                amount_paid     DECIMAL(12,2) NOT NULL DEFAULT 0,
                change_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,
                payment_method  VARCHAR(30) NOT NULL DEFAULT 'cash',
                payment_ref     VARCHAR(100),
                cashier_id      VARCHAR(36),
                customer_id     VARCHAR(36),
                customer_name   VARCHAR(200),
                notes           TEXT,
                created_at      VARCHAR(30) NOT NULL,
                synced          SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sale_items (
                id          VARCHAR(36) PRIMARY KEY,
                sale_id     VARCHAR(36) NOT NULL,
                product_id  VARCHAR(36) NOT NULL,
                qty         INT NOT NULL,
                unit_price  DECIMAL(12,2) NOT NULL,
                cost_price  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                discount    DECIMAL(12,2) NOT NULL DEFAULT 0,
                subtotal    DECIMAL(12,2) NOT NULL,
                synced      SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS suppliers (
                id          VARCHAR(36) PRIMARY KEY,
                name        VARCHAR(255) NOT NULL,
                contact     VARCHAR(200),
                phone       VARCHAR(50),
                email       VARCHAR(150),
                address     TEXT,
                active      SMALLINT NOT NULL DEFAULT 1,
                created_at  VARCHAR(30) NOT NULL,
                updated_at  VARCHAR(30) NOT NULL,
                synced      SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS customers (
                id          VARCHAR(36) PRIMARY KEY,
                name        VARCHAR(255) NOT NULL,
                phone       VARCHAR(50),
                email       VARCHAR(150),
                address     TEXT,
                active      SMALLINT NOT NULL DEFAULT 1,
                created_at  VARCHAR(30) NOT NULL,
                updated_at  VARCHAR(30) NOT NULL,
                synced      SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS purchases (
                id              VARCHAR(36) PRIMARY KEY,
                supplier_id     VARCHAR(36),
                purchase_date   VARCHAR(30) NOT NULL,
                total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
                status          VARCHAR(30) NOT NULL DEFAULT 'pending',
                notes           TEXT,
                created_at      VARCHAR(30) NOT NULL,
                synced          SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS purchase_items (
                id          VARCHAR(36) PRIMARY KEY,
                purchase_id VARCHAR(36) NOT NULL,
                product_id  VARCHAR(36) NOT NULL,
                qty         INT NOT NULL,
                unit_cost   DECIMAL(12,2) NOT NULL,
                subtotal    DECIMAL(12,2) NOT NULL,
                synced      SMALLINT NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS shifts (
                id                   VARCHAR(36) PRIMARY KEY,
                cashier_id           VARCHAR(36) NOT NULL,
                start_time           VARCHAR(30) NOT NULL,
                end_time             VARCHAR(30),
                starting_cash        DECIMAL(12,2) NOT NULL,
                cash_sales           DECIMAL(12,2) NOT NULL DEFAULT 0,
                momo_sales           DECIMAL(12,2) NOT NULL DEFAULT 0,
                card_sales           DECIMAL(12,2) NOT NULL DEFAULT 0,
                expected_ending_cash DECIMAL(12,2),
                declared_ending_cash DECIMAL(12,2),
                discrepancy          DECIMAL(12,2),
                discrepancy_resolved SMALLINT NOT NULL DEFAULT 0,
                status               VARCHAR(30) NOT NULL,
                notes                TEXT,
                synced               SMALLINT NOT NULL DEFAULT 0
            )
            """,

            "CREATE TABLE IF NOT EXISTS sync_log (" +
            "  id          BIGINT PRIMARY KEY " + autoIncrement(dbType) + "," +
            "  table_name  VARCHAR(100) NOT NULL," +
            "  record_id   VARCHAR(36) NOT NULL," +
            "  operation   VARCHAR(10) NOT NULL," +
            "  created_at  VARCHAR(30) NOT NULL," +
            "  synced      SMALLINT NOT NULL DEFAULT 0" +
            ")",
            
            """
            CREATE TABLE IF NOT EXISTS activity_logs (
                id          VARCHAR(36) PRIMARY KEY,
                user_id     VARCHAR(36),
                username    VARCHAR(100),
                action      VARCHAR(50) NOT NULL,
                description TEXT NOT NULL,
                created_at  VARCHAR(30) NOT NULL,
                synced      SMALLINT NOT NULL DEFAULT 0
            )
            """
        };


        try (Statement stmt = conn.createStatement()) {
            for (String sql : ddl) stmt.execute(sql);
        }
        logger.info("Remote schema verified.");
    }

    /** Returns the correct auto-increment syntax for each DB type. */
    private static String autoIncrement(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "postgresql" -> "GENERATED ALWAYS AS IDENTITY";
            default           -> "AUTO_INCREMENT";
        };
    }
}

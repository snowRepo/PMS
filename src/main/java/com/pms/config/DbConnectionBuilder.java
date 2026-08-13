package com.pms.config;

/**
 * Builds JDBC connection URLs from structured fields so users never
 * have to type raw JDBC syntax.
 *
 * Supports: PostgreSQL, MySQL, MariaDB
 */
public final class DbConnectionBuilder {

    private DbConnectionBuilder() {}

    /**
     * Builds a JDBC URL from the given parameters.
     *
     * @param type    "postgresql", "mysql", or "mariadb" (case-insensitive)
     * @param host    Database host (e.g. "db.abc.supabase.co" or "192.168.1.100")
     * @param port    Port number as string (e.g. "5432")
     * @param dbName  Database / schema name
     * @param ssl     Whether to require SSL
     */
    public static String buildUrl(String type, String host, String port,
                                  String dbName, boolean ssl) {
        return switch (type.toLowerCase()) {
            case "postgresql" -> {
                String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
                yield ssl ? url + "?sslmode=require" : url;
            }
            case "mysql" -> {
                String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                           + "?serverTimezone=UTC&allowPublicKeyRetrieval=true";
                yield ssl ? url + "&useSSL=true" : url + "&useSSL=false";
            }
            case "mariadb" -> {
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + dbName;
                yield ssl ? url + "?useSSL=true" : url;
            }
            default -> throw new IllegalArgumentException("Unsupported DB type: " + type);
        };
    }

    /** Returns the JDBC driver class name for a given DB type. */
    public static String driverClass(String type) {
        return switch (type.toLowerCase()) {
            case "postgresql" -> "org.postgresql.Driver";
            case "mysql"      -> "com.mysql.cj.jdbc.Driver";
            case "mariadb"    -> "org.mariadb.jdbc.Driver";
            default -> throw new IllegalArgumentException("Unsupported DB type: " + type);
        };
    }

    /** Returns the default port for a given DB type. */
    public static int defaultPort(String type) {
        return switch (type.toLowerCase()) {
            case "postgresql" -> 5432;
            case "mysql", "mariadb" -> 3306;
            default -> 5432;
        };
    }

    /**
     * Quick connectivity test. Attempts to open a connection with a 5-second timeout.
     * @return null on success, or an error message string on failure.
     */
    public static String testConnection(String type, String host, String port,
                                        String dbName, String user, String pass, boolean ssl) {
        String url = buildUrl(type, host, port, dbName, ssl);
        try {
            Class.forName(driverClass(type));
            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", user);
            props.setProperty("password", pass);
            props.setProperty("loginTimeout", "5");
            props.setProperty("connectTimeout", "5");
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, props)) {
                return conn.isValid(3) ? null : "Connection established but is not valid.";
            }
        } catch (ClassNotFoundException e) {
            return "Driver not found for " + type + ".";
        } catch (java.sql.SQLException e) {
            return e.getMessage();
        }
    }
}

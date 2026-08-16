package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.ActivityLog;
import com.pms.util.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActivityLogDAO {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLogDAO.class);

    /**
     * Highly reusable static helper to quickly log actions without instantiating the DAO everywhere.
     * Uses the currently logged in user from Session.
     */
    public static void log(String action, String description) {
        new Thread(() -> {
            try {
                String userId = null;
                String username = "System";
                if (Session.isLoggedIn()) {
                    userId = Session.current().getId();
                    username = Session.current().getUsername();
                }
                
                ActivityLog log = new ActivityLog(
                    UUID.randomUUID().toString(),
                    userId,
                    username,
                    action,
                    description,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                );
                
                new ActivityLogDAO().create(log);
            } catch (Exception e) {
                logger.error("Failed to insert activity log", e);
            }
        }).start();
    }
    
    /**
     * Specialized log for cases where session is not set or we want to log for a specific user ID
     */
    public static void logAs(String userId, String username, String action, String description) {
        new Thread(() -> {
            try {
                ActivityLog log = new ActivityLog(
                    UUID.randomUUID().toString(),
                    userId,
                    username,
                    action,
                    description,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                );
                new ActivityLogDAO().create(log);
            } catch (Exception e) {
                logger.error("Failed to insert activity log", e);
            }
        }).start();
    }

    public void create(ActivityLog log) throws SQLException {
        String sql = "INSERT INTO activity_logs (id, user_id, username, action, description, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = DatabaseConfig.getLocalConnection().prepareStatement(sql)) {

            pstmt.setString(1, log.getId());
            pstmt.setString(2, log.getUserId());
            pstmt.setString(3, log.getUsername());
            pstmt.setString(4, log.getAction());
            pstmt.setString(5, log.getDescription());
            pstmt.setString(6, log.getCreatedAt());
            pstmt.executeUpdate();
            
            logSync("activity_logs", log.getId(), "INSERT");
        }
    }
    
    private void logSync(String table, String recordId, String operation) {
        try (PreparedStatement ps = DatabaseConfig.getLocalConnection().prepareStatement(
                "INSERT INTO sync_log (table_name, record_id, operation, created_at, synced) VALUES (?,?,?,?,0)")) {
            ps.setString(1, table);
            ps.setString(2, recordId);
            ps.setString(3, operation);
            ps.setString(4, com.pms.util.DateTimeUtil.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to write sync log entry: {}", e.getMessage());
        }
    }

    public List<ActivityLog> findAll(int limit, int offset, String search) throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs ";
        boolean hasSearch = search != null && !search.trim().isEmpty();
        
        if (hasSearch) {
            sql += "WHERE username LIKE ? OR action LIKE ? OR description LIKE ? ";
        }
        
        sql += "ORDER BY created_at DESC LIMIT ? OFFSET ?";

        try (PreparedStatement pstmt = DatabaseConfig.getLocalConnection().prepareStatement(sql)) {

            int paramIndex = 1;
            if (hasSearch) {
                String term = "%" + search.trim() + "%";
                pstmt.setString(paramIndex++, term);
                pstmt.setString(paramIndex++, term);
                pstmt.setString(paramIndex++, term);
            }
            pstmt.setInt(paramIndex++, limit);
            pstmt.setInt(paramIndex, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(extractFromResultSet(rs));
                }
            }
        }
        return logs;
    }

    public int countAll(String search) throws SQLException {
        String sql = "SELECT COUNT(*) FROM activity_logs ";
        boolean hasSearch = search != null && !search.trim().isEmpty();
        
        if (hasSearch) {
            sql += "WHERE username LIKE ? OR action LIKE ? OR description LIKE ? ";
        }

        try (PreparedStatement pstmt = DatabaseConfig.getLocalConnection().prepareStatement(sql)) {
             
            if (hasSearch) {
                String term = "%" + search.trim() + "%";
                pstmt.setString(1, term);
                pstmt.setString(2, term);
                pstmt.setString(3, term);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<ActivityLog> findFilteredPaginated(int limit, int offset, String search, String startDate, String endDate) throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM activity_logs WHERE 1=1 ");
        
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasStartDate = startDate != null && !startDate.trim().isEmpty();
        boolean hasEndDate = endDate != null && !endDate.trim().isEmpty();
        
        if (hasSearch) {
            sql.append("AND (username LIKE ? OR action LIKE ? OR description LIKE ?) ");
        }
        if (hasStartDate) {
            sql.append("AND created_at >= ? ");
        }
        if (hasEndDate) {
            sql.append("AND created_at <= ? ");
        }
        
        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");

        try (PreparedStatement pstmt = DatabaseConfig.getLocalConnection().prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (hasSearch) {
                String term = "%" + search.trim() + "%";
                pstmt.setString(paramIndex++, term);
                pstmt.setString(paramIndex++, term);
                pstmt.setString(paramIndex++, term);
            }
            if (hasStartDate) {
                pstmt.setString(paramIndex++, startDate + "T00:00:00");
            }
            if (hasEndDate) {
                pstmt.setString(paramIndex++, endDate + "T23:59:59");
            }
            pstmt.setInt(paramIndex++, limit);
            pstmt.setInt(paramIndex, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(extractFromResultSet(rs));
                }
            }
        }
        return logs;
    }

    public int countFiltered(String search, String startDate, String endDate) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM activity_logs WHERE 1=1 ");
        
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasStartDate = startDate != null && !startDate.trim().isEmpty();
        boolean hasEndDate = endDate != null && !endDate.trim().isEmpty();
        
        if (hasSearch) {
            sql.append("AND (username LIKE ? OR action LIKE ? OR description LIKE ?) ");
        }
        if (hasStartDate) {
            sql.append("AND created_at >= ? ");
        }
        if (hasEndDate) {
            sql.append("AND created_at <= ? ");
        }

        try (PreparedStatement pstmt = DatabaseConfig.getLocalConnection().prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (hasSearch) {
                String term = "%" + search.trim() + "%";
                pstmt.setString(paramIndex++, term);
                pstmt.setString(paramIndex++, term);
                pstmt.setString(paramIndex++, term);
            }
            if (hasStartDate) {
                pstmt.setString(paramIndex++, startDate + "T00:00:00");
            }
            if (hasEndDate) {
                pstmt.setString(paramIndex++, endDate + "T23:59:59");
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private ActivityLog extractFromResultSet(ResultSet rs) throws SQLException {
        ActivityLog log = new ActivityLog();
        log.setId(rs.getString("id"));
        log.setUserId(rs.getString("user_id"));
        log.setUsername(rs.getString("username"));
        log.setAction(rs.getString("action"));
        log.setDescription(rs.getString("description"));
        log.setCreatedAt(rs.getString("created_at"));
        log.setSynced(rs.getInt("synced") == 1);
        return log;
    }
}

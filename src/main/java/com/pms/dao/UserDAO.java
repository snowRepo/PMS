package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.User;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data Access Object for users table.
 *
 * Password rules enforced here:
 *  - Passwords always stored as BCrypt hashes
 *  - prev_password_hash prevents immediate reuse
 *  - is_temp_password forces change on next login
 *  - last_password_change drives the 30-day expiry check
 *
 * PIN rules:
 *  - 6-digit PIN stored as BCrypt hash (pin_hash)
 *  - Used only for the "Forgot Password" reset flow
 */
public class UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);
    private static final int PASSWORD_EXPIRY_DAYS = 30;

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  AUTHENTICATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Authenticates using username + plain-text password.
     * @return the User if credentials match, null otherwise.
     */
    public User authenticate(String username, String plainPassword) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM users WHERE username = ? AND active = 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                User u = map(rs);
                return BCrypt.checkpw(plainPassword, u.getPassword()) ? u : null;
            }
        }
    }

    /** Returns true if the user's password has not been changed in 30+ days. */
    public boolean isPasswordExpired(String userId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT last_password_change FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String ts = rs.getString(1);
                if (ts == null || ts.isBlank()) return false;
                Instant last = Instant.parse(ts.contains("T") ? ts : ts.replace(" ", "T") + "Z");
                return ChronoUnit.DAYS.between(last, Instant.now()) >= PASSWORD_EXPIRY_DAYS;
            }
        }
    }

    /**
     * Verifies the 6-digit PIN for a given username.
     * Used in the Forgot Password flow.
     * @return the user's ID if the PIN matches, null otherwise.
     */
    public String verifyPin(String username, String plainPin) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT id, pin_hash FROM users WHERE username = ? AND active = 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String pinHash = rs.getString("pin_hash");
                if (pinHash == null || pinHash.isBlank()) return null;
                return BCrypt.checkpw(plainPin, pinHash) ? rs.getString("id") : null;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates the first admin account during the setup wizard.
     * Stores the BCrypt-hashed PIN immediately.
     */
    public User createAdmin(String fullName, String username,
                            String plainPassword, String plainPin) throws SQLException {
        User u = new User();
        u.setId(IdGenerator.newId());
        u.setFullName(fullName);
        u.setUsername(username);
        u.setPassword(BCrypt.hashpw(plainPassword, BCrypt.gensalt()));
        u.setRole("admin");
        u.setActive(true);
        u.setCreatedAt(DateTimeUtil.now());
        u.setUpdatedAt(DateTimeUtil.now());

        String pinHash = BCrypt.hashpw(plainPin, BCrypt.gensalt());

        String sql = """
            INSERT INTO users (id, username, password, role, full_name, active,
                pin_hash, is_temp_password, last_password_change, created_at, updated_at, synced)
            VALUES (?,?,?,?,?,1,?,0,?,?,?,0)
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getRole());
            ps.setString(5, u.getFullName());
            ps.setString(6, pinHash);
            ps.setString(7, DateTimeUtil.now());
            ps.setString(8, u.getCreatedAt());
            ps.setString(9, u.getUpdatedAt());
            ps.executeUpdate();
        }

        logSync("users", u.getId(), "INSERT");
        return u;
    }

    /**
     * Creates a cashier account with a system-generated temporary password.
     * @return the generated temporary password (shown once to the admin).
     */
    public String createCashier(String fullName, String username) throws SQLException {
        String tempPassword = generateTempPassword();
        String tempHash     = BCrypt.hashpw(tempPassword, BCrypt.gensalt());

        User u = new User();
        u.setId(IdGenerator.newId());
        u.setFullName(fullName);
        u.setUsername(username);
        u.setPassword(tempHash);
        u.setRole("cashier");
        u.setActive(true);
        u.setCreatedAt(DateTimeUtil.now());
        u.setUpdatedAt(DateTimeUtil.now());

        String sql = """
            INSERT INTO users (id, username, password, role, full_name, active,
                is_temp_password, last_password_change, prev_password_hash, created_at, updated_at, synced)
            VALUES (?,?,?,?,?,1,1,?,?,?,?,0)
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1,  u.getId());
            ps.setString(2,  u.getUsername());
            ps.setString(3,  u.getPassword());
            ps.setString(4,  u.getRole());
            ps.setString(5,  u.getFullName());
            ps.setString(6,  DateTimeUtil.now());
            ps.setString(7,  tempHash);      // prev = temp, so they can't reuse it
            ps.setString(8,  u.getCreatedAt());
            ps.setString(9,  u.getUpdatedAt());
            ps.executeUpdate();
        }

        logSync("users", u.getId(), "INSERT");
        return tempPassword;   // returned to admin, shown once
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  READ
    // ═══════════════════════════════════════════════════════════════════════════

    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT * FROM users WHERE active = 1 ORDER BY full_name")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<User> search(String query, String statusFilter) throws SQLException {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1 ");
        
        if ("Active".equals(statusFilter)) {
            sql.append("AND active = 1 ");
        } else if ("Inactive".equals(statusFilter)) {
            sql.append("AND active = 0 ");
        }
        
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (username LIKE ? OR full_name LIKE ?) ");
        }
        
        sql.append("ORDER BY full_name");
        
        try (PreparedStatement ps = conn().prepareStatement(sql.toString())) {
            if (query != null && !query.trim().isEmpty()) {
                String term = "%" + query.trim() + "%";
                ps.setString(1, term);
                ps.setString(2, term);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public User findById(String id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM users WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public User findByUsername(String username) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM users WHERE username = ? AND active = 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public boolean hasAnyAdmin() throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role = 'admin' AND active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Checks if the remote database has any admin users. */
    public boolean hasAnyRemoteAdmin() throws SQLException {
        Connection remote = DatabaseConfig.getRemoteConnection();
        if (remote == null) return false;
        try (PreparedStatement ps = remote.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role = 'admin' AND active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } finally {
            remote.close();
        }
    }

    /** Pulls all users from the remote database and saves them locally. */
    public void pullAllUsersFromRemote() throws SQLException {
        Connection remote = DatabaseConfig.getRemoteConnection();
        if (remote == null) return;
        
        String selectSql = "SELECT * FROM users";
        String insertSql = """
            INSERT OR REPLACE INTO users (
                id, username, password, role, full_name, active,
                pin_hash, is_temp_password, last_password_change, prev_password_hash,
                created_at, updated_at, synced
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1)
            """;
            
        try (PreparedStatement psSelect = remote.prepareStatement(selectSql);
             ResultSet rs = psSelect.executeQuery();
             PreparedStatement psInsert = conn().prepareStatement(insertSql)) {
             
            while (rs.next()) {
                psInsert.setString(1, rs.getString("id"));
                psInsert.setString(2, rs.getString("username"));
                psInsert.setString(3, rs.getString("password"));
                psInsert.setString(4, rs.getString("role"));
                psInsert.setString(5, rs.getString("full_name"));
                psInsert.setInt(6, rs.getInt("active"));
                psInsert.setString(7, rs.getString("pin_hash"));
                psInsert.setInt(8, rs.getInt("is_temp_password"));
                psInsert.setString(9, rs.getString("last_password_change"));
                psInsert.setString(10, rs.getString("prev_password_hash"));
                psInsert.setString(11, rs.getString("created_at"));
                psInsert.setString(12, rs.getString("updated_at"));
                psInsert.executeUpdate();
            }
        } finally {
            remote.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PASSWORD MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Changes a user's password.
     * Validates that the new password is not the same as the previous one.
     *
     * @param userId        User's ID
     * @param newPlain      New plain-text password
     * @param clearTempFlag If true, also clears the is_temp_password flag
     * @return null on success, or an error message string on failure
     */
    public String changePassword(String userId, String newPlain, boolean clearTempFlag)
            throws SQLException {

        // Fetch the previous hash to prevent reuse
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT password, prev_password_hash FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "User not found.";

                String currentHash = rs.getString("password");
                String prevHash    = rs.getString("prev_password_hash");

                // Must not match current password
                if (BCrypt.checkpw(newPlain, currentHash)) {
                    return "New password cannot be the same as your current password.";
                }

                // Must not match previous password (if set)
                if (prevHash != null && !prevHash.isBlank() && BCrypt.checkpw(newPlain, prevHash)) {
                    return "New password cannot be the same as your previous password.";
                }
            }
        }

        // Save the new password
        String newHash = BCrypt.hashpw(newPlain, BCrypt.gensalt());
        String sql = """
            UPDATE users
            SET password             = ?,
                prev_password_hash   = password,
                is_temp_password     = ?,
                last_password_change = ?,
                updated_at           = ?,
                synced               = 0
            WHERE id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setInt   (2, clearTempFlag ? 0 : 0);  // always clear after change
            ps.setString(3, DateTimeUtil.now());
            ps.setString(4, DateTimeUtil.now());
            ps.setString(5, userId);
            ps.executeUpdate();
        }

        logSync("users", userId, "UPDATE");
        return null; // null = success
    }

    /**
     * Sets or updates the 6-digit security PIN for a user.
     */
    public void setPin(String userId, String plainPin) throws SQLException {
        String pinHash = BCrypt.hashpw(plainPin, BCrypt.gensalt());
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE users SET pin_hash = ?, updated_at = ?, synced = 0 WHERE id = ?")) {
            ps.setString(1, pinHash);
            ps.setString(2, DateTimeUtil.now());
            ps.setString(3, userId);
            ps.executeUpdate();
        }
        logSync("users", userId, "UPDATE");
    }

    /**
     * Resets password via PIN — used in the Forgot Password flow.
     * Does NOT require the old password (PIN already verified by verifyPin()).
     */
    public String resetPasswordWithPin(String userId, String newPlain) throws SQLException {
        // Only check against prev_password_hash for reuse
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT prev_password_hash FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String prev = rs.getString(1);
                    if (prev != null && !prev.isBlank() && BCrypt.checkpw(newPlain, prev)) {
                        return "New password cannot be the same as your previous password.";
                    }
                }
            }
        }
        return changePassword(userId, newPlain, true);
    }

    // ─── Deactivate ───────────────────────────────────────────────────────────
    /**
     * Admin forces a password reset for a user.
     * @return the newly generated temporary password.
     */
    public String adminResetPassword(String userId) throws SQLException {
        String tempPassword = generateTempPassword();
        String tempHash     = BCrypt.hashpw(tempPassword, BCrypt.gensalt());

        String sql = """
            UPDATE users
            SET password = ?,
                is_temp_password = 1,
                last_password_change = ?,
                prev_password_hash = ?,
                updated_at = ?,
                synced = 0
            WHERE id = ?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, tempHash);
            ps.setString(2, DateTimeUtil.now());
            ps.setString(3, tempHash); // Can't reuse this temp pass
            ps.setString(4, DateTimeUtil.now());
            ps.setString(5, userId);
            ps.executeUpdate();
        }

        logSync("users", userId, "UPDATE");
        return tempPassword;
    }

    public void deactivate(String id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE users SET active = 0, updated_at = ?, synced = 0 WHERE id = ?")) {
            ps.setString(1, DateTimeUtil.now());
            ps.setString(2, id);
            ps.executeUpdate();
        }
        logSync("users", id, "UPDATE");
    }

    public void activate(String id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE users SET active = 1, updated_at = ?, synced = 0 WHERE id = ?")) {
            ps.setString(1, DateTimeUtil.now());
            ps.setString(2, id);
            ps.executeUpdate();
        }
        logSync("users", id, "UPDATE");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static String generateTempPassword() {
        // TMP- followed by 8 uppercase alphanumeric chars
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("TMP-");
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getString("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setFullName(rs.getString("full_name"));
        u.setActive(rs.getInt("active") == 1);
        u.setCreatedAt(rs.getString("created_at"));
        u.setUpdatedAt(rs.getString("updated_at"));

        // New columns — may be null on older schema rows
        try { u.setTempPassword(rs.getInt("is_temp_password") == 1); } catch (SQLException ignored) {}
        try { u.setLastPasswordChange(rs.getString("last_password_change")); } catch (SQLException ignored) {}
        try { u.setPinHash(rs.getString("pin_hash")); } catch (SQLException ignored) {}

        return u;
    }

    private void logSync(String table, String recordId, String operation) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO sync_log (table_name, record_id, operation, created_at, synced) VALUES (?,?,?,?,0)")) {
            ps.setString(1, table);
            ps.setString(2, recordId);
            ps.setString(3, operation);
            ps.setString(4, DateTimeUtil.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to write sync log: {}", e.getMessage());
        }
    }
}

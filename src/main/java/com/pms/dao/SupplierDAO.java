package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Supplier;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SupplierDAO {

    private static final Logger logger = LoggerFactory.getLogger(SupplierDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    public Supplier create(Supplier s) throws SQLException {
        s.setId(IdGenerator.newId());
        s.setCreatedAt(DateTimeUtil.now());
        s.setUpdatedAt(DateTimeUtil.now());

        String sql = "INSERT INTO suppliers (id, name, contact, phone, email, address, active, created_at, updated_at, synced) VALUES (?,?,?,?,?,?,1,?,?,0)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, s.getId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getContact());
            ps.setString(4, s.getPhone());
            ps.setString(5, s.getEmail());
            ps.setString(6, s.getAddress());
            ps.setString(7, s.getCreatedAt());
            ps.setString(8, s.getUpdatedAt());
            ps.executeUpdate();
        }
        logSync(s.getId(), "INSERT");
        return s;
    }

    public void update(Supplier s) throws SQLException {
        s.setUpdatedAt(DateTimeUtil.now());
        String sql = "UPDATE suppliers SET name=?, contact=?, phone=?, email=?, address=?, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContact());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setString(6, s.getUpdatedAt());
            ps.setString(7, s.getId());
            ps.executeUpdate();
        }
        logSync(s.getId(), "UPDATE");
    }

    public void delete(String id) throws SQLException {
        String sql = "UPDATE suppliers SET active=0, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, DateTimeUtil.now());
            ps.setString(2, id);
            ps.executeUpdate();
        }
        logSync(id, "DELETE");
    }

    public List<Supplier> search(String query) throws SQLException {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT * FROM suppliers WHERE active = 1 AND (name LIKE ? OR phone LIKE ?) ORDER BY name";
        String q = "%" + query + "%";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }
    
    public List<Supplier> findAllActive() throws SQLException {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT * FROM suppliers WHERE active = 1 ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public int countAll(String query) throws SQLException {
        String sql = "SELECT COUNT(*) FROM suppliers WHERE active = 1";
        boolean hasSearch = query != null && !query.isBlank();
        if (hasSearch) {
            sql += " AND (name LIKE ? OR phone LIKE ?)";
        }

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (hasSearch) {
                String pattern = "%" + query + "%";
                ps.setString(1, pattern);
                ps.setString(2, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<Supplier> findPaginated(int limit, int offset, String query) throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE active = 1";
        boolean hasSearch = query != null && !query.isBlank();
        if (hasSearch) {
            sql += " AND (name LIKE ? OR phone LIKE ?)";
        }
        sql += " ORDER BY name ASC LIMIT ? OFFSET ?";

        List<Supplier> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            int paramIdx = 1;
            if (hasSearch) {
                String pattern = "%" + query + "%";
                ps.setString(paramIdx++, pattern);
                ps.setString(paramIdx++, pattern);
            }
            ps.setInt(paramIdx++, limit);
            ps.setInt(paramIdx, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private Supplier map(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getString("id"));
        s.setName(rs.getString("name"));
        s.setContact(rs.getString("contact"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setActive(rs.getInt("active"));
        s.setCreatedAt(rs.getString("created_at"));
        s.setUpdatedAt(rs.getString("updated_at"));
        return s;
    }

    private void logSync(String recordId, String action) {
        String sql = "INSERT INTO sync_log (table_name, record_id, operation, created_at) VALUES ('suppliers', ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.setString(2, action);
            ps.setString(3, DateTimeUtil.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert sync log for suppliers", e);
        }
    }
}

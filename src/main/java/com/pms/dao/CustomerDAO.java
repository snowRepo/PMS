package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Customer;
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

public class CustomerDAO {

    private static final Logger logger = LoggerFactory.getLogger(CustomerDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    public Customer create(Customer c) throws SQLException {
        c.setId(IdGenerator.newId());
        c.setCreatedAt(DateTimeUtil.now());
        c.setUpdatedAt(DateTimeUtil.now());

        String sql = "INSERT INTO customers (id, name, phone, email, address, active, created_at, updated_at, synced) VALUES (?,?,?,?,?,1,?,?,0)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getName());
            ps.setString(3, c.getPhone());
            ps.setString(4, c.getEmail());
            ps.setString(5, c.getAddress());
            ps.setString(6, c.getCreatedAt());
            ps.setString(7, c.getUpdatedAt());
            ps.executeUpdate();
        }
        logSync(c.getId(), "INSERT");
        return c;
    }

    public void update(Customer c) throws SQLException {
        c.setUpdatedAt(DateTimeUtil.now());
        String sql = "UPDATE customers SET name=?, phone=?, email=?, address=?, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getEmail());
            ps.setString(4, c.getAddress());
            ps.setString(5, c.getUpdatedAt());
            ps.setString(6, c.getId());
            ps.executeUpdate();
        }
        logSync(c.getId(), "UPDATE");
    }

    public void delete(String id) throws SQLException {
        String sql = "UPDATE customers SET active=0, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, DateTimeUtil.now());
            ps.setString(2, id);
            ps.executeUpdate();
        }
        logSync(id, "DELETE");
    }

    public List<Customer> search(String query) throws SQLException {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customers WHERE active = 1 AND (name LIKE ? OR phone LIKE ?) ORDER BY name";
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
    
    public List<Customer> findAllActive() throws SQLException {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customers WHERE active = 1 ORDER BY name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public int countAll(String query) throws SQLException {
        String sql = "SELECT COUNT(*) FROM customers WHERE active = 1";
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

    public List<Customer> findPaginated(int limit, int offset, String query) throws SQLException {
        String sql = "SELECT * FROM customers WHERE active = 1";
        boolean hasSearch = query != null && !query.isBlank();
        if (hasSearch) {
            sql += " AND (name LIKE ? OR phone LIKE ?)";
        }
        sql += " ORDER BY name ASC LIMIT ? OFFSET ?";

        List<Customer> list = new ArrayList<>();
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

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getString("id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setActive(rs.getInt("active"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        return c;
    }

    private void logSync(String recordId, String action) {
        String sql = "INSERT INTO sync_log (table_name, record_id, action) VALUES ('customers', ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert sync log for customers", e);
        }
    }
}

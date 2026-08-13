package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Category;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private static final Logger logger = LoggerFactory.getLogger(CategoryDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    public Category create(Category c) throws SQLException {
        c.setId(IdGenerator.newId());
        c.setCreatedAt(DateTimeUtil.now());
        c.setUpdatedAt(DateTimeUtil.now());

        String sql = "INSERT INTO categories (id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getName());
            ps.setString(3, c.getDescription());
            ps.setString(4, c.getCreatedAt());
            ps.setString(5, c.getUpdatedAt());
            ps.executeUpdate();
        }
        logSync(c.getId(), "INSERT");
        return c;
    }

    public void update(Category c) throws SQLException {
        c.setUpdatedAt(DateTimeUtil.now());
        String sql = "UPDATE categories SET name=?, description=?, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getDescription());
            ps.setString(3, c.getUpdatedAt());
            ps.setString(4, c.getId());
            ps.executeUpdate();
        }
        logSync(c.getId(), "UPDATE");
    }

    public void delete(String id, String categoryName) throws SQLException, IllegalStateException {
        // Protection: Check if any active product is using this category
        String checkSql = "SELECT count(*) FROM products WHERE category = ? AND active = 1";
        try (PreparedStatement checkPs = conn().prepareStatement(checkSql)) {
            checkPs.setString(1, categoryName);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalStateException("Cannot delete category. There are active products associated with it.");
                }
            }
        }

        // Hard delete since Categories are not tracked in historical records like products are
        String sql = "DELETE FROM categories WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        logSync(id, "DELETE");
    }

    public int countAll(String query) throws SQLException {
        String sql = "SELECT COUNT(*) FROM categories";
        boolean hasSearch = query != null && !query.isBlank();
        if (hasSearch) {
            sql += " WHERE name LIKE ? OR description LIKE ?";
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

    public List<Category> findPaginated(int limit, int offset, String query) throws SQLException {
        String sql = "SELECT * FROM categories";
        boolean hasSearch = query != null && !query.isBlank();
        if (hasSearch) {
            sql += " WHERE name LIKE ? OR description LIKE ?";
        }
        sql += " ORDER BY name ASC LIMIT ? OFFSET ?";

        List<Category> list = new ArrayList<>();
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

    private Category map(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setId(rs.getString("id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        return c;
    }

    private void logSync(String recordId, String action) {
        String sql = "INSERT INTO sync_log (table_name, record_id, action) VALUES ('categories', ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert sync log for categories", e);
        }
    }
}

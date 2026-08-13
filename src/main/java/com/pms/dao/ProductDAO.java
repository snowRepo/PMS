package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Product;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the products table.
 * All writes also create a sync_log entry so SyncManager can push them to the cloud.
 */
public class ProductDAO {

    private static final Logger logger = LoggerFactory.getLogger(ProductDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    public Product create(Product m) throws SQLException {
        m.setId(IdGenerator.newId());
        m.setCreatedAt(DateTimeUtil.now());
        m.setUpdatedAt(DateTimeUtil.now());

        String sql = """
            INSERT INTO products (id, name, generic_name, barcode, category, manufacturer,
                unit, cost_price, selling_price, stock_qty, reorder_level,
                expiry_date, description, active, created_at, updated_at, synced)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,0)
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1,  m.getId());
            ps.setString(2,  m.getName());
            ps.setString(3,  m.getGenericName());
            ps.setString(4,  m.getBarcode());
            ps.setString(5,  m.getCategory());
            ps.setString(6,  m.getManufacturer());
            ps.setString(7,  m.getUnit());
            ps.setDouble(8,  m.getCostPrice());
            ps.setDouble(9,  m.getSellingPrice());
            ps.setInt(10,    m.getStockQty());
            ps.setInt(11,    m.getReorderLevel());
            ps.setString(12, m.getExpiryDate());
            ps.setString(13, m.getDescription());
            ps.setString(14, m.getCreatedAt());
            ps.setString(15, m.getUpdatedAt());
            ps.executeUpdate();
        }

        logSync("products", m.getId(), "INSERT");
        return m;
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    /** Gets the total count of active products. Used for pagination. */
    public int countAll(String searchQuery) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1";
        boolean hasSearch = searchQuery != null && !searchQuery.isBlank();
        if (hasSearch) {
            sql += " AND (name LIKE ? OR barcode LIKE ? OR category LIKE ?)";
        }

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            if (hasSearch) {
                String pattern = "%" + searchQuery + "%";
                stmt.setString(1, pattern);
                stmt.setString(2, pattern);
                stmt.setString(3, pattern);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /** Finds active products with pagination and optional search filter. */
    public List<Product> findPaginated(int limit, int offset, String searchQuery) throws SQLException {
        String sql = "SELECT * FROM products WHERE active = 1";
        boolean hasSearch = searchQuery != null && !searchQuery.isBlank();
        if (hasSearch) {
            sql += " AND (name LIKE ? OR barcode LIKE ? OR category LIKE ?)";
        }
        sql += " ORDER BY name ASC LIMIT ? OFFSET ?";

        List<Product> list = new ArrayList<>();
        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            int paramIdx = 1;
            if (hasSearch) {
                String pattern = "%" + searchQuery + "%";
                stmt.setString(paramIdx++, pattern);
                stmt.setString(paramIdx++, pattern);
                stmt.setString(paramIdx++, pattern);
            }
            stmt.setInt(paramIdx++, limit);
            stmt.setInt(paramIdx, offset);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public Product findById(String id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM products WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Product findByBarcode(String barcode) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM products WHERE barcode = ? AND active = 1")) {
            ps.setString(1, barcode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Product> search(String query) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE active = 1 AND (name LIKE ? OR generic_name LIKE ? OR barcode LIKE ?) ORDER BY name";
        String q = "%" + query + "%";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public int countLowStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1 AND stock_qty <= reorder_level";
        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<Product> getPaginatedLowStock(int limit, int offset) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE active = 1 AND stock_qty <= reorder_level ORDER BY stock_qty ASC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public List<Product> findLowStock() throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE active = 1 AND stock_qty <= reorder_level ORDER BY stock_qty";

        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Product> findExpiringSoon(int daysAhead) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE active = 1 AND expiry_date IS NOT NULL " +
                     "AND expiry_date <= date('now', '+' || ? || ' days') ORDER BY expiry_date";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, daysAhead);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    public void update(Product m) throws SQLException {
        m.setUpdatedAt(DateTimeUtil.now());

        String sql = """
            UPDATE products SET name=?, generic_name=?, barcode=?, category=?, manufacturer=?,
                unit=?, cost_price=?, selling_price=?, stock_qty=?, reorder_level=?,
                expiry_date=?, description=?, updated_at=?, synced=0
            WHERE id=?
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1,  m.getName());
            ps.setString(2,  m.getGenericName());
            ps.setString(3,  m.getBarcode());
            ps.setString(4,  m.getCategory());
            ps.setString(5,  m.getManufacturer());
            ps.setString(6,  m.getUnit());
            ps.setDouble(7,  m.getCostPrice());
            ps.setDouble(8,  m.getSellingPrice());
            ps.setInt(9,     m.getStockQty());
            ps.setInt(10,    m.getReorderLevel());
            ps.setString(11, m.getExpiryDate());
            ps.setString(12, m.getDescription());
            ps.setString(13, m.getUpdatedAt());
            ps.setString(14, m.getId());
            ps.executeUpdate();
        }

        logSync("products", m.getId(), "UPDATE");
    }

    public void adjustStock(String productId, int delta) throws SQLException {
        String sql = "UPDATE products SET stock_qty = stock_qty + ?, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1,    delta);
            ps.setString(2, DateTimeUtil.now());
            ps.setString(3, productId);
            ps.executeUpdate();
        }
        logSync("products", productId, "UPDATE");
    }

    // ─── DELETE (soft) ────────────────────────────────────────────────────────

    public void delete(String id) throws SQLException {
        String sql = "UPDATE products SET active=0, updated_at=?, synced=0 WHERE id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, DateTimeUtil.now());
            ps.setString(2, id);
            ps.executeUpdate();
        }
        logSync("products", id, "DELETE");
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private Product map(ResultSet rs) throws SQLException {
        Product m = new Product();
        m.setId(rs.getString("id"));
        m.setName(rs.getString("name"));
        m.setGenericName(rs.getString("generic_name"));
        m.setBarcode(rs.getString("barcode"));
        m.setCategory(rs.getString("category"));
        m.setManufacturer(rs.getString("manufacturer"));
        m.setUnit(rs.getString("unit"));
        m.setCostPrice(rs.getDouble("cost_price"));
        m.setSellingPrice(rs.getDouble("selling_price"));
        m.setStockQty(rs.getInt("stock_qty"));
        m.setReorderLevel(rs.getInt("reorder_level"));
        m.setExpiryDate(rs.getString("expiry_date"));
        m.setDescription(rs.getString("description"));
        m.setActive(rs.getInt("active") == 1);
        m.setCreatedAt(rs.getString("created_at"));
        m.setUpdatedAt(rs.getString("updated_at"));
        return m;
    }

    // ─── Sync log ─────────────────────────────────────────────────────────────

    private void logSync(String table, String recordId, String operation) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO sync_log (table_name, record_id, operation, created_at, synced) VALUES (?,?,?,?,0)")) {
            ps.setString(1, table);
            ps.setString(2, recordId);
            ps.setString(3, operation);
            ps.setString(4, DateTimeUtil.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to write sync log entry: {}", e.getMessage());
        }
    }
}

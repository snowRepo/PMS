package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Purchase;
import com.pms.model.PurchaseItem;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PurchaseDAO {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    public Purchase create(Purchase purchase) throws SQLException {
        purchase.setId(IdGenerator.newId());
        purchase.setCreatedAt(DateTimeUtil.now());
        if (purchase.getStatus() == null) {
            purchase.setStatus("PENDING");
        }

        conn().setAutoCommit(false);
        try {
            String headerSql = """
                INSERT INTO purchases (id, supplier_id, purchase_date, total_amount, status, notes, created_at, synced)
                VALUES (?,?,?,?,?,?,?,0)
                """;
            try (PreparedStatement ps = conn().prepareStatement(headerSql)) {
                ps.setString(1, purchase.getId());
                ps.setString(2, purchase.getSupplierId());
                ps.setString(3, purchase.getPurchaseDate());
                ps.setDouble(4, purchase.getTotalAmount());
                ps.setString(5, purchase.getStatus());
                ps.setString(6, purchase.getNotes());
                ps.setString(7, purchase.getCreatedAt());
                ps.executeUpdate();
            }

            for (PurchaseItem item : purchase.getItems()) {
                item.setId(IdGenerator.newId());
                item.setPurchaseId(purchase.getId());
                insertItem(item);
            }

            conn().commit();
            logSync("purchases", purchase.getId(), "INSERT");
            return purchase;
        } catch (SQLException e) {
            conn().rollback();
            throw e;
        } finally {
            conn().setAutoCommit(true);
        }
    }

    public void receiveStock(String purchaseId) throws SQLException {
        conn().setAutoCommit(false);
        try {
            // Check if already received
            String checkSql = "SELECT status FROM purchases WHERE id = ?";
            try (PreparedStatement ps = conn().prepareStatement(checkSql)) {
                ps.setString(1, purchaseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "RECEIVED".equals(rs.getString("status"))) {
                        conn().rollback();
                        return; // Already received
                    }
                }
            }

            // Update status
            String updateSql = "UPDATE purchases SET status = 'RECEIVED', synced = 0 WHERE id = ?";
            try (PreparedStatement ps = conn().prepareStatement(updateSql)) {
                ps.setString(1, purchaseId);
                ps.executeUpdate();
            }

            // Increase stock
            List<PurchaseItem> items = findItemsByPurchaseId(purchaseId);
            for (PurchaseItem item : items) {
                increaseStock(item.getProductId(), item.getQty());
            }

            conn().commit();
            logSync("purchases", purchaseId, "UPDATE");
        } catch (SQLException e) {
            conn().rollback();
            throw e;
        } finally {
            conn().setAutoCommit(true);
        }
    }

    public Purchase findById(String id) throws SQLException {
        String sql = """
            SELECT p.*, s.name as supplier_name 
            FROM purchases p 
            LEFT JOIN suppliers s ON p.supplier_id = s.id 
            WHERE p.id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Purchase purchase = mapHeader(rs);
                purchase.setItems(findItemsByPurchaseId(id));
                return purchase;
            }
        }
    }

    private String buildFilterQuery(boolean isCount, String search, String startDate, String endDate, String status, List<Object> params) {
        StringBuilder sql = new StringBuilder();
        if (isCount) {
            sql.append("SELECT COUNT(*) FROM purchases p LEFT JOIN suppliers s ON p.supplier_id = s.id ");
        } else {
            sql.append("SELECT p.*, s.name as supplier_name FROM purchases p LEFT JOIN suppliers s ON p.supplier_id = s.id ");
        }
        
        sql.append("WHERE 1=1 ");
        
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (p.id LIKE ? OR s.name LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
        }
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append("AND p.purchase_date >= ? ");
            params.add(startDate + " 00:00:00");
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append("AND p.purchase_date <= ? ");
            params.add(endDate + " 23:59:59");
        }

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("All")) {
            sql.append("AND p.status = ? ");
            params.add(status.toUpperCase());
        }
        
        return sql.toString();
    }

    public int countBySupplierAndStatus(String supplierId, String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM purchases WHERE supplier_id = ? AND status = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, supplierId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void cancelOrder(String purchaseId) throws SQLException {
        conn().setAutoCommit(false);
        try {
            String checkSql = "SELECT status FROM purchases WHERE id = ?";
            try (PreparedStatement ps = conn().prepareStatement(checkSql)) {
                ps.setString(1, purchaseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "RECEIVED".equals(rs.getString("status"))) {
                        conn().rollback();
                        throw new SQLException("Cannot cancel a received order.");
                    }
                }
            }

            String updateSql = "UPDATE purchases SET status = 'CANCELLED', synced = 0 WHERE id = ?";
            try (PreparedStatement ps = conn().prepareStatement(updateSql)) {
                ps.setString(1, purchaseId);
                ps.executeUpdate();
            }

            conn().commit();
            logSync("purchases", purchaseId, "UPDATE");
        } catch (SQLException e) {
            conn().rollback();
            throw e;
        } finally {
            conn().setAutoCommit(true);
        }
    }

    public int countFiltered(String search, String startDate, String endDate, String status) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = buildFilterQuery(true, search, startDate, endDate, status, params);
        
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<Purchase> findFilteredPaginated(int limit, int offset, String search, String startDate, String endDate, String status) throws SQLException {
        List<Purchase> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String sql = buildFilterQuery(false, search, startDate, endDate, status, params);
        
        sql += "ORDER BY p.purchase_date DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapHeader(rs));
            }
        }
        return list;
    }

    public List<PurchaseItem> findItemsByPurchaseId(String purchaseId) throws SQLException {
        List<PurchaseItem> items = new ArrayList<>();
        String sql = """
            SELECT pi.*, m.name as product_name
            FROM purchase_items pi
            LEFT JOIN products m ON pi.product_id = m.id
            WHERE pi.purchase_id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseItem item = mapItem(rs);
                    item.setProductName(rs.getString("product_name"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private void insertItem(PurchaseItem item) throws SQLException {
        String sql = "INSERT INTO purchase_items (id, purchase_id, product_id, qty, unit_cost, subtotal, synced) VALUES (?,?,?,?,?,?,0)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, item.getPurchaseId());
            ps.setString(3, item.getProductId());
            ps.setInt(4,    item.getQty());
            ps.setDouble(5, item.getUnitCost());
            ps.setDouble(6, item.getSubtotal());
            ps.executeUpdate();
        }
    }

    private void increaseStock(String productId, int qty) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE products SET stock_qty = stock_qty + ?, updated_at = ?, synced = 0 WHERE id = ?")) {
            ps.setInt(1, qty);
            ps.setString(2, DateTimeUtil.now());
            ps.setString(3, productId);
            ps.executeUpdate();
        }
        logSync("products", productId, "UPDATE");
    }

    private Purchase mapHeader(ResultSet rs) throws SQLException {
        Purchase p = new Purchase();
        p.setId(rs.getString("id"));
        p.setSupplierId(rs.getString("supplier_id"));
        p.setPurchaseDate(rs.getString("purchase_date"));
        p.setTotalAmount(rs.getDouble("total_amount"));
        p.setStatus(rs.getString("status"));
        p.setNotes(rs.getString("notes"));
        p.setCreatedAt(rs.getString("created_at"));
        
        try {
            p.setSupplierName(rs.getString("supplier_name"));
        } catch (SQLException ignore) {
        }
        
        return p;
    }

    private PurchaseItem mapItem(ResultSet rs) throws SQLException {
        PurchaseItem item = new PurchaseItem();
        item.setId(rs.getString("id"));
        item.setPurchaseId(rs.getString("purchase_id"));
        item.setProductId(rs.getString("product_id"));
        item.setQty(rs.getInt("qty"));
        item.setUnitCost(rs.getDouble("unit_cost"));
        item.setSubtotal(rs.getDouble("subtotal"));
        return item;
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

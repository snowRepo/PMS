package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Sale;
import com.pms.model.SaleItem;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for sales and sale_items tables.
 * A sale is saved as an atomic transaction: header + items + stock deduction.
 */
public class SaleDAO {

    private static final Logger logger = LoggerFactory.getLogger(SaleDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    /**
     * Saves a complete sale (header + items) in a single transaction,
     * and deducts stock for each item.
     */
    public Sale create(Sale sale) throws SQLException {
        sale.setId(IdGenerator.newId());
        sale.setCreatedAt(DateTimeUtil.now());

        conn().setAutoCommit(false);
        try {
            // Insert sale header
            String headerSql = """
                INSERT INTO sales (id, sale_date, total_amount, discount, tax,
                    amount_paid, change_amount, payment_method, payment_ref, cashier_id,
                    customer_id, customer_name, notes, created_at, synced)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """;
            try (PreparedStatement ps = conn().prepareStatement(headerSql)) {
                ps.setString(1,  sale.getId());
                ps.setString(2,  sale.getSaleDate());
                ps.setDouble(3,  sale.getTotalAmount());
                ps.setDouble(4,  sale.getDiscount());
                ps.setDouble(5,  sale.getTax());
                ps.setDouble(6,  sale.getAmountPaid());
                ps.setDouble(7,  sale.getChangeAmount());
                ps.setString(8,  sale.getPaymentMethod());
                ps.setString(9,  sale.getPaymentRef());
                ps.setString(10, sale.getCashierId());
                ps.setString(11, sale.getCustomerId());
                ps.setString(12, sale.getCustomerName());
                ps.setString(13, sale.getNotes());
                ps.setString(14, sale.getCreatedAt());
                ps.executeUpdate();
            }

            // Insert line items and deduct stock
            for (SaleItem item : sale.getItems()) {
                item.setId(IdGenerator.newId());
                item.setSaleId(sale.getId());
                insertItem(item);
                deductStock(item.getProductId(), item.getQty());
            }

            conn().commit();
            
            // Add sale to active shift
            try {
                new ShiftDAO().addSaleToShift(sale.getCashierId(), sale.getAmountPaid() - sale.getChangeAmount(), sale.getPaymentMethod());
            } catch (SQLException ex) {
                logger.error("Failed to add sale to active shift: " + ex.getMessage(), ex);
                // Non-fatal, transaction is already committed
            }
            
            logSync("sales", sale.getId(), "INSERT");
            return sale;

        } catch (SQLException e) {
            conn().rollback();
            throw e;
        } finally {
            conn().setAutoCommit(true);
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public List<Sale> findAll() throws SQLException {
        List<Sale> list = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM sales ORDER BY sale_date DESC")) {
            while (rs.next()) list.add(mapHeader(rs));
        }
        return list;
    }

    public Sale findById(String id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM sales WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Sale sale = mapHeader(rs);
                sale.setItems(findItemsBySaleId(id));
                return sale;
            }
        }
    }

    public List<Sale> findByCustomer(String customerId) throws SQLException {
        List<Sale> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM sales WHERE customer_id = ? ORDER BY sale_date DESC")) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sale sale = mapHeader(rs);
                    sale.setItems(findItemsBySaleId(sale.getId()));
                    list.add(sale);
                }
            }
        }
        return list;
    }

    /** Returns the sum of all sales revenue. */
    public double getTotalRevenue() throws SQLException {
        String sql = "SELECT SUM(amount_paid - change_amount) FROM sales";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    /** Returns the sum of sales revenue for today. */
    public double getTodayRevenue() throws SQLException {
        // SQLite datetime('now', 'localtime') gives current date/time.
        // We match sale_date >= start of today.
        // Or simply matching sale_date starting with today's date (YYYY-MM-DD)
        String today = DateTimeUtil.now().substring(0, 10);
        String sql = "SELECT SUM(amount_paid - change_amount) FROM sales WHERE sale_date LIKE ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, today + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    public double getTotalRevenueByCashier(String cashierId) throws SQLException {
        String sql = "SELECT SUM(amount_paid - change_amount) FROM sales WHERE cashier_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, cashierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    public double getTodayRevenueByCashier(String cashierId) throws SQLException {
        String today = DateTimeUtil.now().substring(0, 10);
        String sql = "SELECT SUM(amount_paid - change_amount) FROM sales WHERE cashier_id = ? AND sale_date LIKE ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, cashierId);
            ps.setString(2, today + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    public List<Sale> findByDateRange(String from, String to) throws SQLException {
        List<Sale> list = new ArrayList<>();
        String sql = "SELECT * FROM sales WHERE sale_date >= ? AND sale_date <= ? ORDER BY sale_date DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapHeader(rs));
            }
        }
        return list;
    }

    private String buildFilterQuery(boolean isCount, String search, String userId, String startDate, String endDate, List<Object> params) {
        StringBuilder sql = new StringBuilder();
        if (isCount) {
            sql.append("SELECT COUNT(*) FROM sales s ");
        } else {
            sql.append("SELECT s.*, u.full_name as cashier_name FROM sales s LEFT JOIN users u ON s.cashier_id = u.id ");
        }
        
        sql.append("WHERE 1=1 ");
        
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (s.id LIKE ? OR s.customer_name LIKE ? OR s.payment_ref LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }
        
        if (userId != null && !userId.trim().isEmpty()) {
            sql.append("AND s.cashier_id = ? ");
            params.add(userId);
        }
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append("AND s.sale_date >= ? ");
            params.add(startDate + " 00:00:00");
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append("AND s.sale_date <= ? ");
            params.add(endDate + " 23:59:59");
        }
        
        return sql.toString();
    }

    public int countFiltered(String search, String userId, String startDate, String endDate) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = buildFilterQuery(true, search, userId, startDate, endDate, params);
        
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

    public List<Sale> findFilteredPaginated(int limit, int offset, String search, String userId, String startDate, String endDate) throws SQLException {
        List<Sale> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String sql = buildFilterQuery(false, search, userId, startDate, endDate, params);
        
        sql += "ORDER BY s.sale_date DESC LIMIT ? OFFSET ?";
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

    public List<Sale> findAllFiltered(String search, String userId, String startDate, String endDate) throws SQLException {
        List<Sale> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String sql = buildFilterQuery(false, search, userId, startDate, endDate, params);
        
        sql += "ORDER BY s.sale_date DESC";
        
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

    public List<SaleItem> findItemsBySaleId(String saleId) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        String sql = """
            SELECT si.*, m.name as product_name
            FROM sale_items si
            LEFT JOIN products m ON si.product_id = m.id
            WHERE si.sale_id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaleItem item = mapItem(rs);
                    item.setProductName(rs.getString("product_name"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void insertItem(SaleItem item) throws SQLException {
        String sql = "INSERT INTO sale_items (id, sale_id, product_id, qty, unit_price, cost_price, discount, subtotal, synced) VALUES (?,?,?,?,?,?,?,?,0)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, item.getSaleId());
            ps.setString(3, item.getProductId());
            ps.setInt(4,    item.getQty());
            ps.setDouble(5, item.getUnitPrice());
            ps.setDouble(6, item.getCostPrice());
            ps.setDouble(7, item.getDiscount());
            ps.setDouble(8, item.getSubtotal());
            ps.executeUpdate();
        }
    }

    private void deductStock(String productId, int qty) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE products SET stock_qty = stock_qty - ?, updated_at = ?, synced = 0 WHERE id = ?")) {
            ps.setInt(1, qty);
            ps.setString(2, DateTimeUtil.now());
            ps.setString(3, productId);
            ps.executeUpdate();
        }
        logSync("products", productId, "UPDATE");
    }

    private Sale mapHeader(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setId(rs.getString("id"));
        s.setSaleDate(rs.getString("sale_date"));
        s.setTotalAmount(rs.getDouble("total_amount"));
        s.setDiscount(rs.getDouble("discount"));
        s.setTax(rs.getDouble("tax"));
        s.setAmountPaid(rs.getDouble("amount_paid"));
        s.setChangeAmount(rs.getDouble("change_amount"));
        s.setPaymentMethod(rs.getString("payment_method"));
        s.setPaymentRef(rs.getString("payment_ref"));
        s.setCashierId(rs.getString("cashier_id"));
        s.setCustomerId(rs.getString("customer_id"));
        s.setCustomerName(rs.getString("customer_name"));
        s.setNotes(rs.getString("notes"));
        s.setCreatedAt(rs.getString("created_at"));
        
        try {
            s.setCashierName(rs.getString("cashier_name"));
        } catch (SQLException ignore) {
            // Column may not exist in all queries
        }
        
        return s;
    }

    private SaleItem mapItem(ResultSet rs) throws SQLException {
        SaleItem item = new SaleItem();
        item.setId(rs.getString("id"));
        item.setSaleId(rs.getString("sale_id"));
        item.setProductId(rs.getString("product_id"));
        item.setQty(rs.getInt("qty"));
        item.setUnitPrice(rs.getDouble("unit_price"));
        try { item.setCostPrice(rs.getDouble("cost_price")); } catch (SQLException ignore) {}
        item.setDiscount(rs.getDouble("discount"));
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

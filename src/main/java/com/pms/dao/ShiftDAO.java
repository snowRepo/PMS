package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.Shift;
import com.pms.util.DateTimeUtil;
import com.pms.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ShiftDAO {

    private static final Logger logger = LoggerFactory.getLogger(ShiftDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    public Shift getActiveShift(String cashierId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT s.*, u.full_name as cashier_name FROM shifts s " +
                "JOIN users u ON s.cashier_id = u.id " +
                "WHERE s.cashier_id = ? AND s.status = 'ACTIVE'")) {
            ps.setString(1, cashierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public Shift getLastClosedShift() throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT s.*, u.full_name as cashier_name FROM shifts s " +
                "JOIN users u ON s.cashier_id = u.id " +
                "WHERE s.status = 'CLOSED' ORDER BY s.end_time DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public List<Shift> getAllShifts() throws SQLException {
        List<Shift> list = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT s.*, u.full_name as cashier_name FROM shifts s " +
                     "JOIN users u ON s.cashier_id = u.id ORDER BY s.start_time DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public void startShift(Shift shift) throws SQLException {
        shift.setId(IdGenerator.newId());
        shift.setStartTime(DateTimeUtil.now());
        shift.setStatus("ACTIVE");
        
        String sql = """
            INSERT INTO shifts (id, cashier_id, start_time, starting_cash, cash_sales, 
                momo_sales, card_sales, status, notes, synced)
            VALUES (?, ?, ?, ?, 0, 0, 0, 'ACTIVE', ?, 0)
            """;
            
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, shift.getId());
            ps.setString(2, shift.getCashierId());
            ps.setString(3, shift.getStartTime());
            ps.setDouble(4, shift.getStartingCash());
            ps.setString(5, shift.getNotes());
            ps.executeUpdate();
        }
        logSync(shift.getId(), "INSERT");
    }

    public void closeShift(Shift shift) throws SQLException {
        shift.setEndTime(DateTimeUtil.now());
        shift.setStatus("CLOSED");
        
        String sql = """
            UPDATE shifts SET end_time = ?, cash_sales = ?, momo_sales = ?, card_sales = ?,
                expected_ending_cash = ?, declared_ending_cash = ?, discrepancy = ?,
                status = 'CLOSED', notes = ?, synced = 0
            WHERE id = ?
            """;
            
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, shift.getEndTime());
            ps.setDouble(2, shift.getCashSales());
            ps.setDouble(3, shift.getMomoSales());
            ps.setDouble(4, shift.getCardSales());
            ps.setDouble(5, shift.getExpectedEndingCash());
            ps.setDouble(6, shift.getDeclaredEndingCash());
            ps.setDouble(7, shift.getDiscrepancy());
            ps.setString(8, shift.getNotes());
            ps.setString(9, shift.getId());
            ps.executeUpdate();
        }
        logSync(shift.getId(), "UPDATE");
    }
    
    /**
     * Increment the sales of the active shift for the cashier.
     */
    public void addSaleToShift(String cashierId, double amount, String paymentMethod) throws SQLException {
        Shift active = getActiveShift(cashierId);
        if (active == null) return;
        
        String column = switch (paymentMethod.toLowerCase()) {
            case "cash" -> "cash_sales";
            case "momo", "mobile money" -> "momo_sales";
            case "card" -> "card_sales";
            default -> "cash_sales"; // fallback
        };
        
        String sql = "UPDATE shifts SET " + column + " = " + column + " + ?, synced = 0 WHERE id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, active.getId());
            ps.executeUpdate();
        }
        logSync(active.getId(), "UPDATE");
    }

    private Shift map(ResultSet rs) throws SQLException {
        Shift s = new Shift();
        s.setId(rs.getString("id"));
        s.setCashierId(rs.getString("cashier_id"));
        s.setCashierName(rs.getString("cashier_name"));
        s.setStartTime(rs.getString("start_time"));
        s.setEndTime(rs.getString("end_time"));
        s.setStartingCash(rs.getDouble("starting_cash"));
        s.setCashSales(rs.getDouble("cash_sales"));
        s.setMomoSales(rs.getDouble("momo_sales"));
        s.setCardSales(rs.getDouble("card_sales"));
        s.setExpectedEndingCash(rs.getDouble("expected_ending_cash"));
        s.setDeclaredEndingCash(rs.getDouble("declared_ending_cash"));
        s.setDiscrepancy(rs.getDouble("discrepancy"));
        try { s.setDiscrepancyResolved(rs.getInt("discrepancy_resolved") == 1); } catch (SQLException ignored) {}
        s.setStatus(rs.getString("status"));
        s.setNotes(rs.getString("notes"));
        return s;
    }
    
    public void resolveDiscrepancy(String shiftId, String resolutionNotes) throws SQLException {
        Shift s = null;
        String sql = "SELECT s.*, u.full_name as cashier_name FROM shifts s JOIN users u ON s.cashier_id = u.id WHERE s.id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) s = map(rs);
            }
        }
        
        if (s == null) return;
        
        String newNotes = s.getNotes() == null ? "" : s.getNotes();
        if (resolutionNotes != null && !resolutionNotes.isBlank()) {
            newNotes += (newNotes.isEmpty() ? "" : "\n\n") + "Admin Resolution: " + resolutionNotes;
        }
        
        String updateSql = "UPDATE shifts SET discrepancy_resolved = 1, notes = ?, synced = 0 WHERE id = ?";
        try (PreparedStatement ps = conn().prepareStatement(updateSql)) {
            ps.setString(1, newNotes);
            ps.setString(2, shiftId);
            ps.executeUpdate();
        }
        logSync(shiftId, "UPDATE");
    }

    private void logSync(String recordId, String operation) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO sync_log (table_name, record_id, operation, created_at, synced) VALUES ('shifts', ?, ?, ?, 0)")) {
            ps.setString(1, recordId);
            ps.setString(2, operation);
            ps.setString(3, DateTimeUtil.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to write sync log: {}", e.getMessage());
        }
    }
    
    public int countUnresolvedDiscrepancies() throws SQLException {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM shifts WHERE status = 'CLOSED' AND ABS(discrepancy) > 0.01 AND discrepancy_resolved = 0")) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }
}

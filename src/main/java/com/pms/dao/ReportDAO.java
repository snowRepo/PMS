package com.pms.dao;

import com.pms.config.DatabaseConfig;
import com.pms.model.ReportItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {
    private static final Logger logger = LoggerFactory.getLogger(ReportDAO.class);

    private Connection conn() {
        return DatabaseConfig.getLocalConnection();
    }

    public List<ReportItem> generateSalesReport(String startDate, String endDate) throws SQLException {
        List<ReportItem> reportItems = new ArrayList<>();
        
        // Add time boundaries for full day if just dates are passed
        String startDateTime = startDate.length() == 10 ? startDate + " 00:00:00" : startDate;
        String endDateTime = endDate.length() == 10 ? endDate + " 23:59:59" : endDate;

        String sql = """
            SELECT 
                p.name as product_name, 
                p.category, 
                SUM(si.qty) as total_qty, 
                SUM(si.qty * si.unit_price) as total_revenue, 
                SUM(si.qty * si.cost_price) as total_cost 
            FROM sale_items si 
            JOIN sales s ON si.sale_id = s.id 
            JOIN products p ON si.product_id = p.id 
            WHERE s.created_at >= ? AND s.created_at <= ? 
            GROUP BY p.id, p.name, p.category
            ORDER BY total_revenue DESC
            """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, startDateTime);
            ps.setString(2, endDateTime);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String productName = rs.getString("product_name");
                    String category = rs.getString("category");
                    int qtySold = rs.getInt("total_qty");
                    double totalRevenue = rs.getDouble("total_revenue");
                    double totalCost = rs.getDouble("total_cost");
                    
                    double profit = totalRevenue - totalCost;
                    
                    reportItems.add(new ReportItem(productName, category, qtySold, totalRevenue, profit));
                }
            }
        }
        
        return reportItems;
    }
}

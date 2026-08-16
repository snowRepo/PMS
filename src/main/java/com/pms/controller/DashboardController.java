package com.pms.controller;

import com.pms.sync.SyncManager;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main shell controller — holds the sidebar + module content area.
 * Navigation loads FXML modules into the center pane (not using global Navigator).
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private BorderPane rootPane;
    @FXML private StackPane  contentPane;
    @FXML private Label      userLabel;
    @FXML private Label      roleLabel;
    @FXML private Label      syncStatusLabel;
    
    // Sidebar Navigation Buttons
    @FXML private Button navInventory;
    @FXML private Button navCategories;
    @FXML private Button navNewPurchase;
    @FXML private Button navPurchaseHistory;
    @FXML private Button navSuppliers;
    @FXML private Button navCashiers;
    @FXML private Button navShifts;
    @FXML private Button navReports;
    @FXML private Button navSettings;
    @FXML private Button navActivityLogs;

    @FXML
    public void initialize() {
        String role = Session.current().getRole();
        userLabel.setText(Session.current().getFullName());
        roleLabel.setText(role.toUpperCase());
        
        if ("cashier".equalsIgnoreCase(role)) {
            hideNode(navInventory);
            hideNode(navCategories);
            hideNode(navNewPurchase);
            hideNode(navPurchaseHistory);
            hideNode(navSuppliers);
            hideNode(navCashiers);
            hideNode(navShifts);
            hideNode(navReports);
            hideNode(navActivityLogs);
        }
        
        updateSyncStatus();
        navigateTo("DashboardMetrics", "Dashboard");
        
        if ("admin".equalsIgnoreCase(role)) {
            startBadgePolling();
        }
    }
    
    private void startBadgePolling() {
        Runnable updateBadge = () -> {
            try {
                int count = new com.pms.dao.ShiftDAO().countUnresolvedDiscrepancies();
                javafx.application.Platform.runLater(() -> {
                    if (count > 0) {
                        navShifts.setText("⏱️ Shifts (" + count + ")");
                        navShifts.setStyle("-fx-text-fill: #ef4444;");
                    } else {
                        navShifts.setText("⏱️ Shifts");
                        navShifts.setStyle("");
                    }
                });
            } catch (Exception e) {
                // Ignore silent poll error
            }
        };
        
        // Initial fetch
        new Thread(updateBadge).start();
        
        // Poll every 15 seconds
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(15), e -> new Thread(updateBadge).start()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
    
    private void hideNode(Node node) {
        if (node != null) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }

    // ─── Sidebar navigation ───────────────────────────────────────────────────

    @FXML public void openDashboard()  { navigateTo("DashboardMetrics", "Dashboard"); }
    @FXML public void openInventory()  { navigateTo("Inventory", "Inventory"); }
    @FXML public void openCategories() { navigateTo("Categories", "Categories"); }
    @FXML public void openPos()        { navigateTo("Pos", "Point of Sale"); }
    @FXML public void openSalesHistory() { navigateTo("Sales", "Sales History"); } // Reusing Sales for history
    @FXML public void openNewPurchase()  { navigateTo("NewPurchase", "New Purchase"); }
    @FXML public void openPurchaseHistory()  { navigateTo("PurchaseHistory", "Purchase History"); }
    @FXML public void openSuppliers()  { navigateTo("Suppliers", "Suppliers"); }
    @FXML public void openCustomers()  { navigateTo("Customers", "Customers"); }
    @FXML public void openCashiers()   { navigateTo("Cashiers", "Users"); }
    @FXML public void openShifts()     { navigateTo("Shifts", "Shifts"); }
    @FXML public void openReports()    { navigateTo("Reports", "Reports"); }
    @FXML public void openActivityLogs() { navigateTo("ActivityLogs", "Activity Logs"); }
    @FXML public void openSettings()   { navigateTo("Settings", "Settings"); }

    @FXML
    public void handleSync() {
        SyncManager.getInstance().triggerManualSync();
        Notifier.info("Sync triggered...");
        updateSyncStatus();
    }

    @FXML
    public void handleLogout() {
        String name = Session.current().getFullName();
        com.pms.dao.ActivityLogDAO.log("LOGOUT", "User logged out.");
        Session.logout();
        logger.info("User logged out: {}", name);

        Navigator.navigateTo("/fxml/Login.fxml");
        if (!Navigator.getStage().isMaximized() && !Navigator.getStage().isFullScreen()) {
            Navigator.getStage().setWidth(900);
            Navigator.getStage().setHeight(600);
            Navigator.getStage().centerOnScreen();
        }

        Notifier.info("You have been logged out.");
    }

    // ─── Internal module navigation ───────────────────────────────────────────

    private void navigateTo(String viewName, String title) {
        try {
            Node view = FXMLLoader.load(getClass().getResource("/fxml/" + viewName + ".fxml"));
            contentPane.getChildren().setAll(view);
            if (Navigator.getStage() != null) {
                Navigator.getStage().setTitle(title);
            }
        } catch (Exception e) {
            logger.error("Failed to load module: {}", viewName, e);
            Notifier.error("Failed to load " + viewName + " module.");
        }
    }

    private void updateSyncStatus() {
        if (SyncManager.getInstance().isCloudAvailable()) {
            syncStatusLabel.setText("● Online");
            syncStatusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11px;");
        } else {
            syncStatusLabel.setText("● Offline");
            syncStatusLabel.setStyle("-fx-text-fill: #71717a; -fx-font-size: 11px;");
        }
    }
}

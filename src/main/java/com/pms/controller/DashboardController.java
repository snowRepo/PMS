package com.pms.controller;

import com.pms.sync.SyncManager;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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

    @FXML
    public void initialize() {
        userLabel.setText(Session.current().getFullName());
        roleLabel.setText(Session.current().getRole().toUpperCase());
        updateSyncStatus();
        navigateTo("DashboardMetrics");
    }

    // ─── Sidebar navigation ───────────────────────────────────────────────────

    @FXML public void openDashboard()  { navigateTo("DashboardMetrics"); }
    @FXML public void openInventory()  { navigateTo("Inventory"); }
    @FXML public void openCategories() { navigateTo("Categories"); }
    @FXML public void openPos()        { navigateTo("Pos"); }
    @FXML public void openSalesHistory() { navigateTo("Sales"); } // Reusing Sales for history
    @FXML public void openPurchases()  { navigateTo("Purchases"); }
    @FXML public void openCustomers()  { navigateTo("Customers"); }
    @FXML public void openReports()    { navigateTo("Reports"); }
    @FXML public void openSettings()   { navigateTo("Settings"); }

    @FXML
    public void handleSync() {
        SyncManager.getInstance().triggerManualSync();
        Notifier.info("Sync triggered...");
        updateSyncStatus();
    }

    @FXML
    public void handleLogout() {
        String name = Session.current().getFullName();
        Session.logout();
        logger.info("User logged out: {}", name);

        Navigator.navigateTo("/fxml/Login.fxml");
        Navigator.getStage().setWidth(900);
        Navigator.getStage().setHeight(600);
        Navigator.getStage().centerOnScreen();

        Notifier.info("You have been logged out.");
    }

    // ─── Internal module navigation ───────────────────────────────────────────

    private void navigateTo(String viewName) {
        try {
            Node view = FXMLLoader.load(getClass().getResource("/fxml/" + viewName + ".fxml"));
            contentPane.getChildren().setAll(view);
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

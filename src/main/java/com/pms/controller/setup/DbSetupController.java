package com.pms.controller.setup;

import com.pms.config.DatabaseConfig;
import com.pms.config.DbConnectionBuilder;
import com.pms.dao.UserDAO;
import com.pms.sync.SyncManager;
import com.pms.util.AppPrefs;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controller for DbSetup.fxml — Step 1 of the setup wizard. */
public class DbSetupController {

    private static final Logger logger = LoggerFactory.getLogger(DbSetupController.class);

    @FXML private ComboBox<String>  dbTypeCombo;
    @FXML private TextField         hostField;
    @FXML private TextField         portField;
    @FXML private TextField         dbNameField;
    @FXML private TextField         userField;
    @FXML private PasswordField     passField;
    @FXML private CheckBox          sslCheck;
    @FXML private Label             statusLabel;
    @FXML private Button            continueBtn;
    @FXML private Button            testBtn;
    @FXML private Button            skipBtn;

    // Progress UI — shown only during post-restore sync phase
    @FXML private ProgressBar       syncProgressBar;
    @FXML private Label             syncLabel;

    private boolean connectionVerified = false;
    private boolean remoteHasAdmin     = false;
    private final UserDAO userDAO      = new UserDAO();

    @FXML
    public void initialize() {
        Navigator.setTitle("Database Connection");
        dbTypeCombo.setItems(FXCollections.observableArrayList("PostgreSQL", "MySQL", "MariaDB"));
        dbTypeCombo.getSelectionModel().selectFirst();

        // Auto-fill port when type changes
        dbTypeCombo.setOnAction(e -> {
            String type = dbTypeCombo.getValue();
            if (type != null) {
                portField.setText(String.valueOf(DbConnectionBuilder.defaultPort(type.toLowerCase())));
                connectionVerified = false;
                continueBtn.setDisable(true);
                hideStatus();
            }
        });
        portField.setText("5432");

        // Pre-fill saved values if user is returning
        String savedHost = AppPrefs.get("db_host", "");
        if (!savedHost.isBlank()) {
            String savedType = AppPrefs.get("db_type", "postgresql");
            dbTypeCombo.setValue(capitalize(savedType));
            hostField.setText(savedHost);
            portField.setText(AppPrefs.get("db_port", "5432"));
            dbNameField.setText(AppPrefs.get("db_name", ""));
            userField.setText(AppPrefs.get("db_user", ""));
            sslCheck.setSelected(AppPrefs.getBoolean("db_ssl", true));
        }
    }

    @FXML
    private void handleTest() {
        String type   = dbTypeCombo.getValue();
        String host   = hostField.getText().trim();
        String port   = portField.getText().trim();
        String dbName = dbNameField.getText().trim();
        String user   = userField.getText().trim();
        String pass   = passField.getText();
        boolean ssl   = sslCheck.isSelected();

        if (type == null || host.isBlank() || port.isBlank() || dbName.isBlank() || user.isBlank()) {
            showStatus("Please fill in all fields.", false);
            return;
        }

        showStatus("Testing connection...", null);
        continueBtn.setDisable(true);
        connectionVerified = false;

        new Thread(() -> {
            String error = DbConnectionBuilder.testConnection(
                type.toLowerCase(), host, port, dbName, user, pass, ssl);

            boolean hasAdmins = false;
            if (error == null) {
                // If connection is valid, temporarily connect to check for existing users
                boolean connected = DatabaseConfig.connectRemote(type.toLowerCase(), host, port, dbName, user, pass, ssl);
                if (connected) {
                    try {
                        hasAdmins = userDAO.hasAnyRemoteAdmin();
                    } catch (Exception e) {
                        logger.warn("Failed to check remote admins: {}", e.getMessage());
                    }
                }
            }

            final boolean finalHasAdmins = hasAdmins;
            Platform.runLater(() -> {
                if (error == null) {
                    connectionVerified = true;
                    remoteHasAdmin = finalHasAdmins;
                    continueBtn.setDisable(false);

                    if (remoteHasAdmin) {
                        showStatus("✓ Existing account found on this database.", true);
                        continueBtn.setText("Restore My Account →");
                        Notifier.info("Existing accounts found. You can restore them.");
                    } else {
                        showStatus("✓ Connection successful!", true);
                        continueBtn.setText("Continue →");
                        Notifier.success("Database connection verified.");
                    }
                } else {
                    showStatus("✕ " + error, false);
                    continueBtn.setText("Continue →");
                    Notifier.error("Connection failed. Check your credentials.");
                }
            });
        }, "DB-Test-Thread").start();
    }

    @FXML
    private void handleContinue() {
        if (!connectionVerified) {
            Notifier.warning("Please test the connection first.");
            return;
        }

        String type   = dbTypeCombo.getValue().toLowerCase();
        String host   = hostField.getText().trim();
        String port   = portField.getText().trim();
        String dbName = dbNameField.getText().trim();
        String user   = userField.getText().trim();
        String pass   = passField.getText();
        boolean ssl   = sslCheck.isSelected();

        // Save to app_prefs
        AppPrefs.set("db_type", type);
        AppPrefs.set("db_host", host);
        AppPrefs.set("db_port", port);
        AppPrefs.set("db_name", dbName);
        AppPrefs.set("db_user", user);
        AppPrefs.set("db_pass", pass);
        AppPrefs.set("db_ssl",  String.valueOf(ssl));

        // Activate the connection and create remote schema
        boolean ok = DatabaseConfig.connectRemote(type, host, port, dbName, user, pass, ssl);
        if (!ok) {
            Notifier.error("Could not activate database connection.");
            return;
        }

        if (remoteHasAdmin) {
            // Lock the form so nothing can be clicked while restoring
            setFormDisabled(true);
            showSyncProgress("Restoring your accounts...");

            new Thread(() -> {
                try {
                    // Step 1: pull users immediately so login credentials exist locally
                    userDAO.pullAllUsersFromRemote();
                    Platform.runLater(() -> updateSyncLabel("Syncing your data — please wait..."));

                    // Step 2: pull all other tables (products, sales, customers, inventory…)
                    // Running synchronously here means the user arrives at Login with all data ready.
                    SyncManager.getInstance().syncCycle();

                    Platform.runLater(() -> {
                        updateSyncLabel("✓ Restore complete!");
                        Notifier.success("Accounts and data restored successfully.");
                        Navigator.navigateTo("/fxml/Login.fxml");
                    });

                } catch (Exception e) {
                    logger.error("Failed to restore from remote", e);
                    Platform.runLater(() -> {
                        hideSyncProgress();
                        setFormDisabled(false);
                        showStatus("Restore failed: " + e.getMessage(), false);
                        Notifier.error("Failed to restore data from remote database.");
                    });
                }
            }, "DB-Restore-Thread").start();

        } else {
            Notifier.info("Database connected. Proceeding to admin setup...");
            Navigator.navigateTo("/fxml/setup/Register.fxml");
        }
    }

    @FXML
    private void handleSkip() {
        Notifier.info("Skipping cloud database setup. You are running in local-only mode.");
        // Clear any previous cloud DB connection attempts from prefs
        AppPrefs.set("db_host", "");

        // Proceed to admin setup
        Navigator.navigateTo("/fxml/setup/Register.fxml");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Disables / re-enables all form controls during the restore+sync phase. */
    private void setFormDisabled(boolean disabled) {
        dbTypeCombo.setDisable(disabled);
        hostField.setDisable(disabled);
        portField.setDisable(disabled);
        dbNameField.setDisable(disabled);
        userField.setDisable(disabled);
        passField.setDisable(disabled);
        sslCheck.setDisable(disabled);
        testBtn.setDisable(disabled);
        skipBtn.setDisable(disabled);
        continueBtn.setDisable(disabled);
    }

    private void showSyncProgress(String message) {
        syncProgressBar.setVisible(true);
        syncProgressBar.setManaged(true);
        syncLabel.setText(message);
        syncLabel.setVisible(true);
        syncLabel.setManaged(true);
    }

    private void updateSyncLabel(String message) {
        syncLabel.setText(message);
    }

    private void hideSyncProgress() {
        syncProgressBar.setVisible(false);
        syncProgressBar.setManaged(false);
        syncLabel.setVisible(false);
        syncLabel.setManaged(false);
    }

    private void showStatus(String msg, Boolean success) {
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        if (success == null) {
            statusLabel.getStyleClass().removeAll("conn-status-ok", "conn-status-err");
        } else if (success) {
            statusLabel.getStyleClass().removeAll("conn-status-err");
            if (!statusLabel.getStyleClass().contains("conn-status-ok"))
                statusLabel.getStyleClass().add("conn-status-ok");
        } else {
            statusLabel.getStyleClass().removeAll("conn-status-ok");
            if (!statusLabel.getStyleClass().contains("conn-status-err"))
                statusLabel.getStyleClass().add("conn-status-err");
        }
    }

    private void hideStatus() {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}

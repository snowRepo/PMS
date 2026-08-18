package com.pms.controller;

import com.pms.config.DatabaseConfig;
import com.pms.config.DbConnectionBuilder;
import com.pms.dao.UserDAO;
import com.pms.model.User;
import com.pms.util.AppPrefs;
import com.pms.util.CurrencyUtil;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    @FXML private TextField searchField;
    @FXML private javafx.scene.layout.VBox localizationPanel;
    @FXML private javafx.scene.layout.VBox securityPanel;
    @FXML private javafx.scene.layout.VBox cloudDbPanel;
    @FXML private javafx.scene.layout.VBox aboutPanel;
    @FXML private javafx.scene.layout.VBox dangerZonePanel;

    @FXML private ComboBox<String> currencyCombo;

    // Security Section
    @FXML private Label accFullNameLabel;
    @FXML private Label accUsernameLabel;
    @FXML private Label accRoleLabel;

    // Cloud Database Section
    @FXML private ComboBox<String> dbTypeCombo;
    @FXML private TextField  dbHostField;
    @FXML private TextField  dbPortField;
    @FXML private TextField  dbNameField;
    @FXML private TextField  dbUserField;
    @FXML private PasswordField dbPassField;
    @FXML private CheckBox   dbSslCheck;
    @FXML private Label      dbStatusLabel;
    @FXML private Button     dbSaveBtn;
    @FXML private Label      dbActiveStatusLabel;
    @FXML private Button     connectToggleBtn;

    private boolean isConnectionTested = false;

    private final UserDAO userDAO = new UserDAO();

    private final String[] currencies = {
        "$ (US Dollar)",
        "£ (British Pound)",
        "€ (Euro)",
        "GHS",
        "₦ (Nigerian Naira)",
        "¥ (Chinese Yen)",
        "A$ (Australian Dollar)",
        "C$ (Canadian Dollar)"
    };

    @FXML
    public void initialize() {
        // Account Section
        if (Session.current() != null) {
            accFullNameLabel.setText(Session.current().getFullName());
            accUsernameLabel.setText(Session.current().getUsername());
            accRoleLabel.setText(capitalize(Session.current().getRole()));

            if ("cashier".equalsIgnoreCase(Session.current().getRole())) {
                localizationPanel.setVisible(false);
                localizationPanel.setManaged(false);
                cloudDbPanel.setVisible(false);
                cloudDbPanel.setManaged(false);
                aboutPanel.setVisible(false);
                aboutPanel.setManaged(false);
                dangerZonePanel.setVisible(false);
                dangerZonePanel.setManaged(false);
            }
        }

        // Localization
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal.toLowerCase().trim();
            boolean showLoc = "localization".contains(q) || "currency".contains(q) || "symbol".contains(q);
            boolean showSec = "security".contains(q) || "account".contains(q) || "password".contains(q) || "pin".contains(q) || "username".contains(q);
            boolean showDb = "database".contains(q) || "cloud".contains(q) || "host".contains(q) || "connection".contains(q) || "port".contains(q) || "sql".contains(q);
            boolean showAbout = "about".contains(q) || "version".contains(q) || "update".contains(q);
            boolean showDanger = "danger".contains(q) || "reset".contains(q) || "wipe".contains(q) || "nuke".contains(q) || "delete".contains(q);

            boolean isCashier = Session.current() != null && "cashier".equalsIgnoreCase(Session.current().getRole());
            if (isCashier) {
                showLoc = false;
                showDb = false;
                showAbout = false;
                showDanger = false;
            }

            localizationPanel.setVisible(showLoc);
            localizationPanel.setManaged(showLoc);
            securityPanel.setVisible(showSec);
            securityPanel.setManaged(showSec);
            cloudDbPanel.setVisible(showDb);
            cloudDbPanel.setManaged(showDb);
            aboutPanel.setVisible(showAbout);
            aboutPanel.setManaged(showAbout);
            dangerZonePanel.setVisible(showDanger);
            dangerZonePanel.setManaged(showDanger);
        });
        currencyCombo.getItems().addAll(currencies);
        String currentSymbol = CurrencyUtil.getSymbol();
        for (String c : currencies) {
            if (c.startsWith(currentSymbol)) {
                currencyCombo.setValue(c);
                break;
            }
        }

        // Database
        dbTypeCombo.setItems(FXCollections.observableArrayList("PostgreSQL", "MySQL", "MariaDB"));
        String savedType = AppPrefs.get("db_type", "postgresql");
        dbTypeCombo.setValue(capitalize(savedType));
        
        dbTypeCombo.setOnAction(e -> {
            String type = dbTypeCombo.getValue();
            if (type != null) {
                dbPortField.setText(String.valueOf(DbConnectionBuilder.defaultPort(type.toLowerCase())));
                invalidateDbTest();
            }
        });

        dbHostField.setText(AppPrefs.get("db_host", ""));
        dbPortField.setText(AppPrefs.get("db_port", "5432"));
        dbNameField.setText(AppPrefs.get("db_name", ""));
        dbUserField.setText(AppPrefs.get("db_user", ""));
        dbPassField.setText(AppPrefs.get("db_pass", ""));
        dbSslCheck.setSelected(AppPrefs.getBoolean("db_ssl", true));
        
        dbHostField.textProperty().addListener((obs, oldVal, newVal) -> invalidateDbTest());
        dbPortField.textProperty().addListener((obs, oldVal, newVal) -> invalidateDbTest());
        dbNameField.textProperty().addListener((obs, oldVal, newVal) -> invalidateDbTest());
        dbUserField.textProperty().addListener((obs, oldVal, newVal) -> invalidateDbTest());
        dbPassField.textProperty().addListener((obs, oldVal, newVal) -> invalidateDbTest());
        dbSslCheck.selectedProperty().addListener((obs, oldVal, newVal) -> invalidateDbTest());

        updateConnectionToggleState();
    }

    @FXML
    public void handleSave() {
        String selected = currencyCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            Notifier.error("Please select a currency.");
            return;
        }
        String newSymbol = selected.contains(" ") ? selected.split(" ")[0] : selected;
        AppPrefs.set("currency_symbol", newSymbol);
        Notifier.success("Preferences saved successfully!");
    }

    // --- Account ---

    @FXML
    public void handleOpenChangePin() {
        openModal("/fxml/auth/ChangePinModal.fxml", "Change PIN");
    }

    @FXML
    public void handleOpenChangePassword() {
        openModal("/fxml/auth/ChangePasswordModal.fxml", "Change Password");
    }

    private void openModal(String fxmlPath, String title) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle(title);
            // initOwner must be set BEFORE initModality — keeps modal on the same
            // desktop space as the primary window (critical for macOS full-screen mode)
            stage.initOwner(com.pms.util.Navigator.getStage());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.setResizable(false);
            
            // Re-use current styles
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            com.pms.util.UIUtil.enableEnterToClick(scene);
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            stage.setScene(scene);
            stage.centerOnScreen();
            
            stage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open modal: " + fxmlPath, e);
            Notifier.error("Failed to open modal.");
        }
    }

    // --- Cloud Database ---

    private void invalidateDbTest() {
        isConnectionTested = false;
        dbSaveBtn.setDisable(true);
        hideDbStatus();
    }

    private void updateConnectionToggleState() {
        if (DatabaseConfig.isCloudAvailable()) {
            dbActiveStatusLabel.setText("Connected");
            dbActiveStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            connectToggleBtn.setText("Disconnect");
            connectToggleBtn.setDisable(false);
        } else {
            String savedHost = AppPrefs.get("db_host", "");
            if (savedHost.isBlank()) {
                dbActiveStatusLabel.setText("Not Configured");
                dbActiveStatusLabel.setStyle("-fx-text-fill: #71717a;");
                connectToggleBtn.setDisable(true);
            } else {
                dbActiveStatusLabel.setText("Disconnected");
                dbActiveStatusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                connectToggleBtn.setDisable(false);
                connectToggleBtn.setText("Connect");
            }
        }
    }

    @FXML
    public void handleConnectToggle() {
        if (DatabaseConfig.isCloudAvailable()) {
            // Disconnect (fast — no async needed, but keep UX consistent)
            connectToggleBtn.setDisable(true);
            connectToggleBtn.setText("Disconnecting...");

            new Thread(() -> {
                DatabaseConfig.closeAll();
                DatabaseConfig.initLocal(); // Re-init local connection because closeAll closes local too!
                AppPrefs.set("db_enabled", "false");

                Platform.runLater(() -> {
                    updateConnectionToggleState();
                    Notifier.info("Disconnected from cloud database.");
                });
            }, "Settings-DB-Disconnect").start();

        } else {
            // Reconnect — async so UI doesn't freeze
            connectToggleBtn.setDisable(true);
            connectToggleBtn.setText("Connecting...");
            dbActiveStatusLabel.setText("Connecting...");
            dbActiveStatusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
            AppPrefs.set("db_enabled", "true");

            new Thread(() -> {
                boolean ok = DatabaseConfig.initRemote();

                Platform.runLater(() -> {
                    updateConnectionToggleState();
                    if (ok) {
                        Notifier.success("Connected to cloud database!");
                    } else {
                        AppPrefs.set("db_enabled", "false");
                        Notifier.error("Failed to connect to cloud database.");
                    }
                });
            }, "Settings-DB-Connect").start();
        }
    }

    @FXML
    public void handleDbTest() {
        String type   = dbTypeCombo.getValue();
        String host   = dbHostField.getText().trim();
        String port   = dbPortField.getText().trim();
        String dbName = dbNameField.getText().trim();
        String user   = dbUserField.getText().trim();
        String pass   = dbPassField.getText();
        boolean ssl   = dbSslCheck.isSelected();

        if (type == null || host.isBlank() || port.isBlank() || dbName.isBlank() || user.isBlank()) {
            showDbStatus("Please fill all DB fields.", false);
            return;
        }

        showDbStatus("Testing connection...", null);
        type = type.toLowerCase();
        final String finalType = type;

        new Thread(() -> {
            String error = DbConnectionBuilder.testConnection(
                finalType, host, port, dbName, user, pass, ssl);

            Platform.runLater(() -> {
                if (error == null) {
                    isConnectionTested = true;
                    dbSaveBtn.setDisable(false);
                    showDbStatus("Test successful! You can now save.", true);
                    Notifier.success("Database connection verified.");
                } else {
                    isConnectionTested = false;
                    dbSaveBtn.setDisable(true);
                    showDbStatus("Connection failed.", false);
                    Notifier.error("Connection failed: " + error);
                }
            });
        }, "Settings-DB-Test").start();
    }

    @FXML
    public void handleDbSave() {
        if (!isConnectionTested) {
            Notifier.warning("Please test the connection first.");
            return;
        }

        String type   = dbTypeCombo.getValue().toLowerCase();
        String host   = dbHostField.getText().trim();
        String port   = dbPortField.getText().trim();
        String dbName = dbNameField.getText().trim();
        String user   = dbUserField.getText().trim();
        String pass   = dbPassField.getText();
        boolean ssl   = dbSslCheck.isSelected();

        AppPrefs.set("db_type", type);
        AppPrefs.set("db_host", host);
        AppPrefs.set("db_port", port);
        AppPrefs.set("db_name", dbName);
        AppPrefs.set("db_user", user);
        AppPrefs.set("db_pass", pass);
        AppPrefs.set("db_ssl",  String.valueOf(ssl));
        AppPrefs.set("db_enabled", "true");

        dbSaveBtn.setDisable(true);
        showDbStatus("Saving and connecting...", null);

        new Thread(() -> {
            boolean connected = DatabaseConfig.connectRemote(type, host, port, dbName, user, pass, ssl);
            
            Platform.runLater(() -> {
                updateConnectionToggleState();
                dbSaveBtn.setDisable(false);
                if (connected) {
                    showDbStatus("Configuration saved!", true);
                    Notifier.success("Cloud database configuration saved and connected!");
                } else {
                    showDbStatus("Saved, but failed to connect.", false);
                    Notifier.error("Saved, but failed to activate connection.");
                }
            });
        }, "Settings-DB-Save").start();
    }

    private void showDbStatus(String msg, Boolean success) {
        dbStatusLabel.setText(msg);
        dbStatusLabel.setVisible(true);
        dbStatusLabel.setManaged(true);
        if (success == null) {
            dbStatusLabel.setStyle("-fx-text-fill: #f59e0b;");
        } else if (success) {
            dbStatusLabel.setStyle("-fx-text-fill: #10b981;");
        } else {
            dbStatusLabel.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    private void hideDbStatus() {
        dbStatusLabel.setVisible(false);
        dbStatusLabel.setManaged(false);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    @FXML
    public void handleCheckUpdates() {
        Notifier.info("Checking for updates...");
        
        new Thread(() -> {
            try {
                com.pms.util.UpdateManager.UpdateInfo info = com.pms.util.UpdateManager.checkForUpdates();
                
                javafx.application.Platform.runLater(() -> {
                    if (info != null) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Update Available");
                        alert.setHeaderText("Version " + info.version + " is available!");
                        alert.setContentText("Would you like to download and install this update now?");
                        
                        com.pms.util.UIUtil.enableEnterToClick(alert);
                        
                        if (alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK) {
                            openUpdateProgressModal(info);
                        }
                    } else {
                        Notifier.success("You are using the latest version of PMS.");
                    }
                });

            } catch (Exception e) {
                logger.error("Failed to check for updates", e);
                javafx.application.Platform.runLater(() -> {
                    Notifier.error("Update check failed. Ensure you are connected to the internet.");
                });
            }
        }, "Update-Check-Thread").start();
    }

    private void openUpdateProgressModal(com.pms.util.UpdateManager.UpdateInfo info) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/auth/UpdateProgressModal.fxml"));
            javafx.scene.Parent root = loader.load();
            
            com.pms.controller.auth.UpdateProgressController ctrl = loader.getController();
            ctrl.setUpdateInfo(info);
            
            javafx.stage.Stage modal = new javafx.stage.Stage();
            modal.initOwner(com.pms.util.Navigator.getStage());
            modal.initModality(javafx.stage.Modality.WINDOW_MODAL);
            modal.setTitle("Downloading Update");
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            com.pms.util.UIUtil.enableEnterToClick(scene);
            modal.setScene(scene);
            modal.setResizable(false);
            
            modal.show();
        } catch (Exception e) {
            logger.error("Failed to open update progress modal", e);
            Notifier.error("Failed to start download.");
        }
    }

    @FXML
    public void handleOpenResetData() {
        openModal("/fxml/auth/ResetDataModal.fxml", "Reset Data — Danger Zone");
    }
}

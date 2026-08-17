package com.pms.controller.auth;

import com.pms.config.DatabaseConfig;
import com.pms.dao.ActivityLogDAO;
import com.pms.dao.UserDAO;
import com.pms.model.User;
import com.pms.util.AppPrefs;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ResetDataModalController {

    private static final Logger logger = LoggerFactory.getLogger(ResetDataModalController.class);

    // Step 1
    @FXML private VBox step1Panel;
    // ToggleGroup created programmatically — @FXML injection from <fx:define> is unreliable
    private final ToggleGroup wipeGroup = new ToggleGroup();
    @FXML private RadioButton wipeLocalRadio;
    @FXML private RadioButton wipeCloudRadio;
    @FXML private RadioButton wipeBothRadio;
    @FXML private PasswordField passwordField;
    @FXML private Label step1ErrorLabel;
    @FXML private Button nextBtn;

    // Step 2
    @FXML private VBox step2Panel;
    @FXML private HBox pinRow;
    @FXML private Label step2ErrorLabel;
    @FXML private HBox progressBox;
    @FXML private Label progressLabel;
    @FXML private Button confirmBtn;

    private final List<TextField> pinFields = new ArrayList<>();
    private final UserDAO userDAO = new UserDAO();

    // Validated user from step 1 (held for step 2 check)
    private User validatedUser;

    @FXML
    public void initialize() {
        // Wire radio buttons to ToggleGroup programmatically (reliable across all JavaFX runtimes)
        wipeLocalRadio.setToggleGroup(wipeGroup);
        wipeCloudRadio.setToggleGroup(wipeGroup);
        wipeBothRadio.setToggleGroup(wipeGroup);

        buildPinRow();

        // Enable Next only when a mode is selected AND password has text
        wipeGroup.selectedToggleProperty().addListener((obs, o, n) -> refreshNextBtn());
        passwordField.textProperty().addListener((obs, o, n) -> refreshNextBtn());

        // Enable Confirm once all 6 PIN digits are filled
        for (TextField tf : pinFields) {
            tf.textProperty().addListener((obs, o, n) -> refreshConfirmBtn());
        }
    }

    // ── Step 1 ────────────────────────────────────────────────────────────────

    private void refreshNextBtn() {
        nextBtn.setDisable(
            wipeGroup.getSelectedToggle() == null || passwordField.getText().isBlank()
        );
    }

    @FXML
    private void handleNext() {
        clearStep1Error();

        String password = passwordField.getText();
        User user = Session.current();

        if (user == null) {
            showStep1Error("No active session. Please re-login.");
            return;
        }

        try {
            User dbUser = userDAO.findByUsername(user.getUsername());
            if (dbUser == null || !BCrypt.checkpw(password, dbUser.getPassword())) {
                showStep1Error("Incorrect password. Please try again.");
                passwordField.clear();
                return;
            }
            validatedUser = dbUser; // hold for step 2
        } catch (Exception e) {
            logger.error("Password validation error", e);
            showStep1Error("An error occurred. Please try again.");
            return;
        }

        // Advance to step 2
        step1Panel.setVisible(false);
        step1Panel.setManaged(false);
        step2Panel.setVisible(true);
        step2Panel.setManaged(true);
        if (!pinFields.isEmpty()) pinFields.get(0).requestFocus();
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    private void refreshConfirmBtn() {
        confirmBtn.setDisable(collectPin().length() < 6);
    }

    @FXML
    private void handleBack() {
        clearStep2Error();
        clearPinFields();
        step2Panel.setVisible(false);
        step2Panel.setManaged(false);
        step1Panel.setVisible(true);
        step1Panel.setManaged(true);
    }

    @FXML
    private void handleConfirm() {
        clearStep2Error();

        String pin = collectPin();
        if (pin.length() < 6) {
            showStep2Error("Please enter all 6 PIN digits.");
            return;
        }

        if (validatedUser == null || validatedUser.getPinHash() == null
                || !BCrypt.checkpw(pin, validatedUser.getPinHash())) {
            showStep2Error("Incorrect PIN. Please try again.");
            clearPinFields();
            return;
        }

        boolean doLocal = wipeLocalRadio.isSelected();
        boolean doCloud = wipeCloudRadio.isSelected();
        boolean doBoth  = wipeBothRadio.isSelected();

        final boolean finalLocal = doLocal || doBoth;
        final boolean finalCloud = doCloud || doBoth;
        final boolean fullReset  = doBoth;

        setUiBusy(true);
        User user = Session.current();

        new Thread(() -> {
            try {
                if (!fullReset) {
                    String mode = finalLocal && finalCloud ? "local and cloud databases"
                                  : finalLocal ? "local database" : "cloud database";
                    ActivityLogDAO.logAs(user.getId(), user.getUsername(),
                        "DATA_WIPED", "Admin wiped the " + mode + ".");
                }

                if (finalCloud) {
                    Platform.runLater(() -> progressLabel.setText("Wiping cloud data..."));
                    DatabaseConfig.wipeRemoteData();
                    logger.info("Cloud database wiped by admin: {}", user.getUsername());
                }

                if (finalLocal) {
                    Platform.runLater(() -> progressLabel.setText("Wiping local data..."));
                    // Keep users ONLY if this is not a full system reset
                    DatabaseConfig.resetLocalData(!fullReset);
                    logger.info("Local database wiped by admin: {}", user.getUsername());
                    
                    if (!finalCloud && DatabaseConfig.isCloudAvailable()) {
                        Platform.runLater(() -> progressLabel.setText("Restoring data from cloud..."));
                        com.pms.sync.SyncManager.getInstance().syncCycle();
                    }
                }

                if (fullReset) {
                    Platform.runLater(() -> progressLabel.setText("Clearing preferences..."));
                    if (DatabaseConfig.isCloudAvailable()) {
                        DatabaseConfig.closeAll();
                        DatabaseConfig.initLocal();
                    }
                    AppPrefs.clearAll();
                }

                Platform.runLater(() -> {
                    setUiBusy(false);
                    if (fullReset) {
                        Notifier.success("Full reset complete. Returning to setup wizard...");
                        Session.logout();
                        closeModal();
                        Navigator.navigateTo("/fxml/setup/DbSetup.fxml");
                    } else if (finalCloud && finalLocal) {
                        Notifier.success("Both databases wiped successfully.");
                        closeModal();
                    } else if (finalCloud) {
                        Notifier.success("Cloud database wiped successfully.");
                        closeModal();
                    } else {
                        Notifier.success("Local database wiped. Please re-login.");
                        Session.logout();
                        closeModal();
                        Navigator.navigateTo("/fxml/Login.fxml");
                    }
                });

            } catch (Exception e) {
                logger.error("Data wipe failed", e);
                Platform.runLater(() -> {
                    setUiBusy(false);
                    showStep2Error("Wipe failed: " + e.getMessage());
                });
            }
        }, "DataWipe-Thread").start();
    }

    @FXML
    private void handleCancel() {
        closeModal();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void buildPinRow() {
        pinRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 6; i++) {
            PasswordField tf = new PasswordField();
            tf.setPrefWidth(36);
            tf.setPrefHeight(42);
            tf.setAlignment(Pos.CENTER);
            tf.getStyleClass().add("pin-digit-box");

            int idx = i;
            tf.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.length() > 1) {
                    tf.setText(String.valueOf(newVal.charAt(newVal.length() - 1)));
                    return;
                }
                if (!newVal.matches("[0-9]?")) {
                    tf.setText(oldVal);
                    return;
                }
                if (newVal.length() == 1 && idx < pinFields.size() - 1) {
                    pinFields.get(idx + 1).requestFocus();
                } else if (newVal.length() == 1 && idx == pinFields.size() - 1) {
                    confirmBtn.requestFocus();
                }
            });

            tf.setOnAction(e -> {
                if (!confirmBtn.isDisabled()) {
                    handleConfirm();
                }
            });

            tf.setOnKeyPressed(e -> {
                if (e.getCode().toString().equals("BACK_SPACE") && tf.getText().isEmpty() && idx > 0) {
                    pinFields.get(idx - 1).requestFocus();
                }
            });

            pinFields.add(tf);
            pinRow.getChildren().add(tf);
        }
    }

    private String collectPin() {
        StringBuilder sb = new StringBuilder();
        for (TextField f : pinFields) sb.append(f.getText());
        return sb.toString();
    }

    private void clearPinFields() {
        pinFields.forEach(f -> f.setText(""));
        if (!pinFields.isEmpty()) pinFields.get(0).requestFocus();
    }

    private void showStep1Error(String msg) {
        step1ErrorLabel.setText(msg);
        step1ErrorLabel.setVisible(true);
        step1ErrorLabel.setManaged(true);
    }

    private void clearStep1Error() {
        step1ErrorLabel.setVisible(false);
        step1ErrorLabel.setManaged(false);
    }

    private void showStep2Error(String msg) {
        step2ErrorLabel.setText(msg);
        step2ErrorLabel.setVisible(true);
        step2ErrorLabel.setManaged(true);
    }

    private void clearStep2Error() {
        step2ErrorLabel.setVisible(false);
        step2ErrorLabel.setManaged(false);
    }

    private void setUiBusy(boolean busy) {
        progressBox.setVisible(busy);
        progressBox.setManaged(busy);
        confirmBtn.setDisable(busy);
        pinFields.forEach(f -> f.setDisable(busy));
    }

    private void closeModal() {
        Stage stage = (Stage) step1Panel.getScene().getWindow();
        stage.close();
    }
}

package com.pms.controller.setup;

import com.pms.dao.UserDAO;
import com.pms.util.Notifier;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for PinSetup.fxml — Step 3 (modal).
 * Call prepareForNewAdmin() before showing the modal.
 * After showAndWait(), check isCompleted() to see if PIN was saved.
 */
public class PinSetupController {

    private static final Logger logger = LoggerFactory.getLogger(PinSetupController.class);

    @FXML private HBox  pinRow1;
    @FXML private HBox  pinRow2;
    @FXML private Label errorLabel;

    private final List<TextField> pin1Fields = new ArrayList<>();
    private final List<TextField> pin2Fields = new ArrayList<>();

    private String pendingFullName;
    private String pendingUsername;
    private String pendingPassword;
    private boolean completed = false;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        buildPinRow(pinRow1, pin1Fields);
        buildPinRow(pinRow2, pin2Fields);
    }

    /** Called by RegisterController before showAndWait. */
    public void prepareForNewAdmin(String fullName, String username, String password) {
        this.pendingFullName = fullName;
        this.pendingUsername = username;
        this.pendingPassword = password;
    }

    /** Returns true if the admin was saved and PIN was set. */
    public boolean isCompleted() {
        return completed;
    }

    @FXML
    private void handleSetPin() {
        String pin1 = collectPin(pin1Fields);
        String pin2 = collectPin(pin2Fields);

        if (pin1.length() < 6) {
            showError("Please enter all 6 digits of your PIN.");
            return;
        }
        if (!pin1.equals(pin2)) {
            showError("PINs do not match. Please try again.");
            clearPins();
            return;
        }

        try {
            userDAO.createAdmin(pendingFullName, pendingUsername, pendingPassword, pin1);
            completed = true;
            Notifier.success("Admin account created successfully.");
            closeModal();
        } catch (Exception e) {
            logger.error("Failed to create admin", e);
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                showError("Username already exists. Go back and choose another.");
            } else {
                showError("Failed to create account: " + e.getMessage());
            }
        }
    }

    // ── PIN field builder ─────────────────────────────────────────────────────

    private void buildPinRow(HBox row, List<TextField> fields) {
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            PasswordField tf = new PasswordField();
            tf.setPrefWidth(46);
            tf.setPrefHeight(54);
            tf.setAlignment(Pos.CENTER);
            tf.getStyleClass().add("pin-digit-box");
            tf.setFocusTraversable(true);

            int idx = i;
            tf.textProperty().addListener((obs, oldVal, newVal) -> {
                // Only allow single digit
                if (newVal.length() > 1) {
                    tf.setText(String.valueOf(newVal.charAt(newVal.length() - 1)));
                    return;
                }
                if (!newVal.matches("[0-9]?")) {
                    tf.setText(oldVal);
                    return;
                }
                // Auto-advance
                if (newVal.length() == 1 && idx < fields.size() - 1) {
                    fields.get(idx + 1).requestFocus();
                }
            });

            // Backspace moves back
            tf.setOnKeyPressed(e -> {
                if (e.getCode().toString().equals("BACK_SPACE") && tf.getText().isEmpty() && idx > 0) {
                    fields.get(idx - 1).requestFocus();
                }
            });

            fields.add(tf);
            row.getChildren().add(tf);
        }
    }

    private String collectPin(List<TextField> fields) {
        StringBuilder sb = new StringBuilder();
        for (TextField f : fields) sb.append(f.getText());
        return sb.toString();
    }

    private void clearPins() {
        pin1Fields.forEach(f -> f.setText(""));
        pin2Fields.forEach(f -> f.setText(""));
        if (!pin1Fields.isEmpty()) pin1Fields.get(0).requestFocus();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void closeModal() {
        Stage stage = (Stage) pinRow1.getScene().getWindow();
        stage.close();
    }
}

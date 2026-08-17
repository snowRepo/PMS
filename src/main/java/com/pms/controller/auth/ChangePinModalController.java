package com.pms.controller.auth;

import com.pms.dao.UserDAO;
import com.pms.model.User;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChangePinModalController {

    private static final Logger logger = LoggerFactory.getLogger(ChangePinModalController.class);

    @FXML private HBox currentPinRow;
    @FXML private HBox newPinRow;
    @FXML private HBox confirmPinRow;
    @FXML private Label errorLabel;

    private final List<TextField> currentPinFields = new ArrayList<>();
    private final List<TextField> newPinFields = new ArrayList<>();
    private final List<TextField> confirmPinFields = new ArrayList<>();

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        buildPinRow(currentPinRow, currentPinFields, () -> newPinFields.get(0).requestFocus());
        buildPinRow(newPinRow, newPinFields, () -> confirmPinFields.get(0).requestFocus());
        buildPinRow(confirmPinRow, confirmPinFields, this::handleSave);
    }

    @FXML
    private void handleSave() {
        String currentPin = collectPin(currentPinFields);
        String newPin = collectPin(newPinFields);
        String confirmPin = collectPin(confirmPinFields);

        if (currentPin.length() < 6 || newPin.length() < 6 || confirmPin.length() < 6) {
            showError("Please fill out all 6 digits for each PIN.");
            return;
        }

        if (!newPin.equals(confirmPin)) {
            showError("New PINs do not match. Please try again.");
            clearPins(newPinFields);
            clearPins(confirmPinFields);
            return;
        }

        User user = Session.current();
        if (user == null) {
            showError("User not authenticated.");
            return;
        }

        // Validate current PIN
        try {
            User dbUser = userDAO.findByUsername(user.getUsername());
            if (dbUser == null || dbUser.getPinHash() == null || !BCrypt.checkpw(currentPin, dbUser.getPinHash())) {
                showError("Current PIN is incorrect.");
                clearPins(currentPinFields);
                return;
            }

            // Set new PIN
            userDAO.setPin(user.getId(), newPin);
            Notifier.success("Security PIN changed successfully.");
            logger.info("PIN changed for user: {}", user.getUsername());
            com.pms.dao.ActivityLogDAO.logAs(user.getId(), user.getUsername(), "PIN_CHANGED", "User changed their security PIN.");
            
            closeModal();
            
        } catch (Exception e) {
            logger.error("Failed to change PIN", e);
            showError("An unexpected error occurred.");
        }
    }

    @FXML
    private void handleCancel() {
        closeModal();
    }

    private void buildPinRow(HBox row, List<TextField> fields, Runnable onComplete) {
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            PasswordField tf = new PasswordField();
            tf.setPrefWidth(36);
            tf.setPrefHeight(42);
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
                // Auto-advance or fire completion
                if (newVal.length() == 1) {
                    if (idx < fields.size() - 1) {
                        fields.get(idx + 1).requestFocus();
                    } else {
                        onComplete.run();
                    }
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

    private void clearPins(List<TextField> fields) {
        fields.forEach(f -> f.setText(""));
        if (!fields.isEmpty()) fields.get(0).requestFocus();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void closeModal() {
        Stage stage = (Stage) currentPinRow.getScene().getWindow();
        stage.close();
    }
}

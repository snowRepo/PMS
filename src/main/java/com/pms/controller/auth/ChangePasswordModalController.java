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

public class ChangePasswordModalController {

    private static final Logger logger = LoggerFactory.getLogger(ChangePasswordModalController.class);

    @FXML private HBox currentPinRow;
    @FXML private PasswordField newPassField;
    @FXML private PasswordField confirmPassField;
    @FXML private Label errorLabel;

    private final List<TextField> currentPinFields = new ArrayList<>();

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        buildPinRow(currentPinRow, currentPinFields);
    }

    @FXML
    private void handleSave() {
        String currentPin = collectPin(currentPinFields);
        String newPass = newPassField.getText();
        String confirm = confirmPassField.getText();

        if (currentPin.length() < 6) {
            showError("Please enter your full 6-digit current PIN.");
            return;
        }

        if (newPass.isEmpty() || confirm.isEmpty()) {
            showError("Please fill out the new password fields.");
            return;
        }

        if (newPass.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }

        if (!newPass.equals(confirm)) {
            showError("New passwords do not match.");
            return;
        }

        User user = Session.current();
        if (user == null) {
            showError("User not authenticated.");
            return;
        }

        try {
            User dbUser = userDAO.findByUsername(user.getUsername());
            if (dbUser == null || dbUser.getPinHash() == null || !BCrypt.checkpw(currentPin, dbUser.getPinHash())) {
                showError("Current PIN is incorrect.");
                clearPins(currentPinFields);
                return;
            }

            String error = userDAO.changePassword(user.getId(), newPass, false);
            if (error != null) {
                showError(error);
                return;
            }

            Notifier.success("Password changed successfully.");
            logger.info("Password changed for user: {}", user.getUsername());
            com.pms.dao.ActivityLogDAO.logAs(user.getId(), user.getUsername(), "PASSWORD_CHANGED", "User changed their password.");
            
            closeModal();
            
        } catch (Exception e) {
            logger.error("Failed to change password", e);
            showError("An unexpected error occurred.");
        }
    }

    @FXML
    private void handleCancel() {
        closeModal();
    }

    private void buildPinRow(HBox row, List<TextField> fields) {
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
                if (newVal.length() > 1) {
                    tf.setText(String.valueOf(newVal.charAt(newVal.length() - 1)));
                    return;
                }
                if (!newVal.matches("[0-9]?")) {
                    tf.setText(oldVal);
                    return;
                }
                if (newVal.length() == 1 && idx < fields.size() - 1) {
                        fields.get(idx + 1).requestFocus();
                    } else if (newVal.length() == 1 && idx == fields.size() - 1) {
                        // Last PIN digit filled — move focus to first password field
                        newPassField.requestFocus();
                    }
            });

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

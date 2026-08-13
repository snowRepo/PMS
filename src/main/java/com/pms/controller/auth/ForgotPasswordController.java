package com.pms.controller.auth;

import com.pms.dao.UserDAO;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for ForgotPassword.fxml — three-step recovery flow.
 *
 * Step 1: Enter username → look up in DB
 * Step 2: Enter 6-digit PIN → verify with BCrypt
 * Step 3: Enter + confirm new password → save
 *
 * Max 3 PIN attempts before the recovery session is cancelled.
 */
public class ForgotPasswordController {

    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordController.class);
    private static final int MAX_PIN_ATTEMPTS = 3;

    @FXML private VBox step1, step2, step3;
    @FXML private TextField     usernameField;
    @FXML private HBox          pinRow;
    @FXML private Label         step1Error, step2Error, step3Error;
    @FXML private Label         step2Hint;
    @FXML private PasswordField newPassField, confirmField;

    private String verifiedUsername;
    private String verifiedUserId;
    private int pinAttempts = 0;

    private final List<TextField> pinFields = new ArrayList<>();
    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        buildPinRow();
    }

    // ── Step 1: Username ──────────────────────────────────────────────────────

    @FXML
    private void handleStep1() {
        String username = usernameField.getText().trim();
        if (username.isBlank()) {
            showError(step1Error, "Please enter your username.");
            return;
        }

        try {
            var user = userDAO.findByUsername(username);
            if (user == null) {
                showError(step1Error, "Username not found.");
                Notifier.error("No account found with that username.");
                return;
            }
            verifiedUsername = username;
            step2Hint.setText("Enter the 6-digit PIN set during account creation, " + username + ".");
            goToStep(2);
        } catch (Exception e) {
            logger.error("Step 1 error", e);
            showError(step1Error, "An error occurred. Please try again.");
        }
    }

    // ── Step 2: PIN ───────────────────────────────────────────────────────────

    @FXML
    private void handleStep2() {
        String pin = collectPin();
        if (pin.length() < 6) {
            showError(step2Error, "Please enter all 6 digits.");
            return;
        }

        try {
            String userId = userDAO.verifyPin(verifiedUsername, pin);
            if (userId == null) {
                pinAttempts++;
                int remaining = MAX_PIN_ATTEMPTS - pinAttempts;
                if (remaining <= 0) {
                    Notifier.error("Too many incorrect attempts. Please start over.");
                    Navigator.navigateTo("/fxml/Login.fxml");
                    return;
                }
                showError(step2Error, "Incorrect PIN. " + remaining + " attempt(s) remaining.");
                clearPin();
                return;
            }
            verifiedUserId = userId;
            goToStep(3);
        } catch (Exception e) {
            logger.error("Step 2 error", e);
            showError(step2Error, "An error occurred. Please try again.");
        }
    }

    // ── Step 3: New password ──────────────────────────────────────────────────

    @FXML
    private void handleStep3() {
        String newPass = newPassField.getText();
        String confirm = confirmField.getText();

        if (newPass.length() < 8) {
            showError(step3Error, "Password must be at least 8 characters.");
            return;
        }
        if (!newPass.equals(confirm)) {
            showError(step3Error, "Passwords do not match.");
            return;
        }

        try {
            String error = userDAO.resetPasswordWithPin(verifiedUserId, newPass);
            if (error != null) {
                showError(step3Error, error);
                return;
            }
            Notifier.success("Password reset successfully. Please log in.");
            Navigator.navigateTo("/fxml/Login.fxml");
        } catch (Exception e) {
            logger.error("Step 3 error", e);
            showError(step3Error, "Failed to reset password. Please try again.");
        }
    }

    @FXML
    private void handleBack() {
        Navigator.navigateTo("/fxml/Login.fxml");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void goToStep(int step) {
        step1.setVisible(step == 1); step1.setManaged(step == 1);
        step2.setVisible(step == 2); step2.setManaged(step == 2);
        step3.setVisible(step == 3); step3.setManaged(step == 3);
    }

    private void buildPinRow() {
        pinRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            PasswordField tf = new PasswordField();
            tf.setPrefWidth(46);
            tf.setPrefHeight(54);
            tf.setAlignment(Pos.CENTER);
            tf.getStyleClass().add("pin-digit-box");

            int idx = i;
            tf.textProperty().addListener((obs, old, neu) -> {
                if (neu.length() > 1) { tf.setText(String.valueOf(neu.charAt(neu.length()-1))); return; }
                if (!neu.matches("[0-9]?")) { tf.setText(old); return; }
                if (neu.length() == 1 && idx < pinFields.size() - 1) pinFields.get(idx + 1).requestFocus();
            });
            tf.setOnKeyPressed(e -> {
                if (e.getCode().toString().equals("BACK_SPACE") && tf.getText().isEmpty() && idx > 0)
                    pinFields.get(idx - 1).requestFocus();
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

    private void clearPin() {
        pinFields.forEach(f -> f.setText(""));
        if (!pinFields.isEmpty()) pinFields.get(0).requestFocus();
    }

    private void showError(Label label, String msg) {
        label.setText(msg);
        label.setVisible(true);
        label.setManaged(true);
    }
}

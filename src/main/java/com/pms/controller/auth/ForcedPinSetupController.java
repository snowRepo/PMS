package com.pms.controller.auth;

import com.pms.dao.UserDAO;
import com.pms.model.User;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ForcedPinSetupController {

    private static final Logger logger = LoggerFactory.getLogger(ForcedPinSetupController.class);

    @FXML private HBox pinRow1;
    @FXML private HBox pinRow2;
    @FXML private Label errorLabel;

    private final List<TextField> pin1Fields = new ArrayList<>();
    private final List<TextField> pin2Fields = new ArrayList<>();

    private String userId;
    private String newPassword;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        buildPinRow(pinRow1, pin1Fields);
        buildPinRow(pinRow2, pin2Fields);
    }

    public void setDetails(String userId, String newPassword) {
        this.userId = userId;
        this.newPassword = newPassword;
    }

    @FXML
    private void handleSave() {
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
            // First set new password, which removes temp flag
            String error = userDAO.changePassword(userId, newPassword, true);
            if (error != null) {
                showError(error);
                return;
            }

            // Then set PIN
            userDAO.setPin(userId, pin1);

            Notifier.success("Setup complete!");
            com.pms.dao.ActivityLogDAO.log("ACCOUNT_SETUP", "User completed initial setup.");

            Navigator.navigateTo("/fxml/Dashboard.fxml");
            if (!Navigator.getStage().isMaximized() && !Navigator.getStage().isFullScreen()) {
                Navigator.getStage().setWidth(1100);
                Navigator.getStage().setHeight(680);
                Navigator.getStage().centerOnScreen();
            }

        } catch (Exception e) {
            logger.error("Failed to save setup details", e);
            showError("Failed to save details: " + e.getMessage());
        }
    }

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
}

package com.pms.controller.auth;

import com.pms.dao.UserDAO;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for ChangePassword.fxml.
 *
 * Loaded via Navigator.navigateToWithController() — caller must call setForced() before display.
 *
 * Forced mode (temp password / expiry): no Cancel button, subtitle explains why.
 * Voluntary mode (from settings): Cancel button visible, returns to dashboard.
 */
public class ChangePasswordController {

    private static final Logger logger = LoggerFactory.getLogger(ChangePasswordController.class);

    @FXML private Label         titleLabel;
    @FXML private Label         subtitleLabel;
    @FXML private PasswordField newPassField;
    @FXML private PasswordField confirmField;
    @FXML private Label         errorLabel;
    @FXML private Button        cancelBtn;

    private boolean forced = true;
    private String  userId;

    private final UserDAO userDAO = new UserDAO();

    /**
     * Configure before the screen is shown.
     * @param forced true = no cancel allowed (temp password or expiry)
     * @param userId the user whose password is being changed
     */
    public void setForced(boolean forced, String userId) {
        this.forced = forced;
        this.userId = userId;

        if (forced) {
            titleLabel.setText("Password Change Required");
            subtitleLabel.setText("You must set a new password before continuing.");
            cancelBtn.setVisible(false);
            cancelBtn.setManaged(false);
        } else {
            titleLabel.setText("Change Password");
            subtitleLabel.setText("Enter a new password for your account.");
            cancelBtn.setVisible(true);
            cancelBtn.setManaged(true);
        }
    }

    @FXML
    private void handleSave() {
        String newPass  = newPassField.getText();
        String confirm  = confirmField.getText();

        if (newPass.isBlank()) {
            showError("Please enter a new password.");
            return;
        }
        if (newPass.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }
        if (!newPass.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            if (forced) {
                // Navigate to Forced PIN Setup and pass the new password
                ForcedPinSetupController ctrl = Navigator.navigateToWithController("/fxml/auth/ForcedPinSetup.fxml");
                ctrl.setDetails(userId, newPass);
            } else {
                // This else block shouldn't be hit anymore since voluntary uses modals, but left just in case
                String error = userDAO.changePassword(userId, newPass, false);
                if (error != null) {
                    showError(error);
                    Notifier.error(error);
                    return;
                }
                
                Notifier.success("Password changed successfully.");
                logger.info("Password changed for user: {}", userId);
                com.pms.dao.ActivityLogDAO.log("PASSWORD_CHANGED", "User changed their password.");

                Navigator.navigateTo("/fxml/Dashboard.fxml");
                if (!Navigator.getStage().isMaximized() && !Navigator.getStage().isFullScreen()) {
                    Navigator.getStage().setWidth(1100);
                    Navigator.getStage().setHeight(680);
                    Navigator.getStage().centerOnScreen();
                }
            }
        } catch (Exception e) {
            logger.error("Password change failed", e);
            showError("An error occurred. Please try again.");
            Notifier.error("Password change failed.");
        }
    }

    @FXML
    private void handleCancel() {
        Navigator.navigateTo("/fxml/Dashboard.fxml");
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}

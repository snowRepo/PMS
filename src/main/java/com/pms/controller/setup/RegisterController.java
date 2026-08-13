package com.pms.controller.setup;

import com.pms.dao.UserDAO;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controller for Register.fxml — Step 2 of the setup wizard. */
public class RegisterController {

    private static final Logger logger = LoggerFactory.getLogger(RegisterController.class);

    @FXML private TextField     fullNameField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private Label         errorLabel;

    private final UserDAO userDAO = new UserDAO();
    private String pendingUserId; // set after createAdmin, used by PinSetupController

    @FXML
    private void handleBack() {
        Navigator.navigateTo("/fxml/setup/DbSetup.fxml");
    }

    @FXML
    private void handleContinue() {
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmField.getText();

        // Validation
        if (fullName.isBlank() || username.isBlank() || password.isBlank()) {
            showError("All fields are required.");
            return;
        }
        if (username.contains(" ")) {
            showError("Username cannot contain spaces.");
            return;
        }
        if (password.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        // Open PIN setup modal (pass a temporary placeholder — PIN will be stored in modal)
        openPinSetup(fullName, username, password);
    }

    private void openPinSetup(String fullName, String username, String password) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/setup/PinSetup.fxml"));
            Parent root = loader.load();
            PinSetupController ctrl = loader.getController();
            ctrl.prepareForNewAdmin(fullName, username, password);

            Stage modal = new Stage();
            modal.initOwner(Navigator.getStage());
            modal.initModality(Modality.WINDOW_MODAL);
            modal.setTitle("Set Security PIN");
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/css/main.css").toExternalForm());
            modal.setScene(scene);
            modal.setResizable(false);
            modal.showAndWait();  // blocks until PIN setup closes

            // If PIN was set successfully, navigate to Login
            if (ctrl.isCompleted()) {
                Navigator.navigateTo("/fxml/Login.fxml");
                Notifier.success("Setup complete. Please log in.");
            }

        } catch (Exception e) {
            logger.error("Failed to open PIN setup", e);
            Notifier.error("Failed to open PIN setup screen.");
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}

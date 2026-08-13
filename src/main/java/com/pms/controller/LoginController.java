package com.pms.controller;

import com.pms.dao.UserDAO;
import com.pms.model.User;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.Session;
import com.pms.controller.auth.ChangePasswordController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/** Controller for Login.fxml */
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        passwordField.setOnAction(e -> handleLogin());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter your username and password.");
            return;
        }

        loginButton.setDisable(true);
        hideError();

        new Thread(() -> {
            try {
                User user = userDAO.authenticate(username, password);

                Platform.runLater(() -> {
                    loginButton.setDisable(false);

                    if (user == null) {
                        showError("Invalid username or password.");
                        Notifier.error("Login failed. Check your credentials.");
                        return;
                    }

                    Session.login(user);
                    logger.info("Login: {} ({})", user.getUsername(), user.getRole());

                    // Check: forced password change (temp password)?
                    if (user.isTempPassword()) {
                        Notifier.warning("Please set a new password before continuing.");
                        ChangePasswordController ctrl =
                            Navigator.navigateToWithController("/fxml/auth/ChangePassword.fxml");
                        if (ctrl != null) ctrl.setForced(true, user.getId());
                        return;
                    }

                    // Check: 30-day expiry?
                    try {
                        if (userDAO.isPasswordExpired(user.getId())) {
                            Notifier.warning("Your password has expired. Please update it.");
                            ChangePasswordController ctrl =
                                Navigator.navigateToWithController("/fxml/auth/ChangePassword.fxml");
                            if (ctrl != null) ctrl.setForced(true, user.getId());
                            return;
                        }
                    } catch (SQLException e) {
                        logger.error("Expiry check failed", e);
                    }

                    // All good — open dashboard
                    Notifier.success("Welcome back, " + user.getFullName() + ".");
                    Navigator.navigateTo("/fxml/Dashboard.fxml");

                    // Expand window for dashboard
                    Navigator.getStage().setWidth(1100);
                    Navigator.getStage().setHeight(680);
                    Navigator.getStage().centerOnScreen();
                });

            } catch (SQLException e) {
                logger.error("Login DB error", e);
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError("Database error. Please try again.");
                    Notifier.error("Database error during login.");
                });
            }
        }, "Login-Thread").start();
    }

    @FXML
    private void handleForgotPassword() {
        Navigator.navigateTo("/fxml/auth/ForgotPassword.fxml");
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}

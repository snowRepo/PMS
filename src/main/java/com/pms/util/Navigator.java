package com.pms.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized screen navigator.
 *
 * Init once in Main.java with the content StackPane and the primary Stage.
 * Then call Navigator.navigateTo("/fxml/Login.fxml") from any controller.
 *
 * For screens that need controller setup before display, use navigateToWithController()
 * which returns the controller instance after loading.
 */
public final class Navigator {

    private static final Logger logger = LoggerFactory.getLogger(Navigator.class);

    private static StackPane contentPane;
    private static Stage primaryStage;

    private Navigator() {}

    public static void init(StackPane pane, Stage stage) {
        contentPane = pane;
        primaryStage = stage;
    }

    /** Load and display the given FXML in the main content area. */
    public static void navigateTo(String fxmlPath) {
        try {
            // Clear stale barcode listeners from the outgoing screen
            BarcodeScannerManager.getInstance().clearAllListeners();
            Node view = FXMLLoader.load(Navigator.class.getResource(fxmlPath));
            contentPane.getChildren().setAll(view);
        } catch (Exception e) {
            logger.error("Failed to navigate to: {}", fxmlPath, e);
            Notifier.error("Failed to load screen.");
        }
    }

    /**
     * Load an FXML and return its controller so the caller can configure it
     * before the screen is shown.
     *
     * Example:
     *   ChangePasswordController ctrl = Navigator.navigateToWithController("/fxml/auth/ChangePassword.fxml");
     *   ctrl.setForced(true, userId);
     */
    @SuppressWarnings("unchecked")
    public static <T> T navigateToWithController(String fxmlPath) {
        try {
            // Clear stale barcode listeners from the outgoing screen
            BarcodeScannerManager.getInstance().clearAllListeners();
            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource(fxmlPath));
            Node view = loader.load();
            contentPane.getChildren().setAll(view);
            return loader.getController();
        } catch (Exception e) {
            logger.error("Failed to navigate to: {}", fxmlPath, e);
            Notifier.error("Failed to load screen.");
            return null;
        }
    }

    public static Stage getStage() {
        return primaryStage;
    }
    
    public static void setTitle(String title) {
        if (primaryStage != null) {
            primaryStage.setTitle(title);
        }
    }
}

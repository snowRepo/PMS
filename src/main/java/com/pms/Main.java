package com.pms;

import com.pms.config.DatabaseConfig;
import com.pms.dao.UserDAO;
import com.pms.sync.SyncManager;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.NotificationPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PMS — Pharmacy Management System
 * Application entry point.
 *
 * Root layout:
 *   StackPane (root)
 *    ├── contentPane  — Navigator swaps screens in here
 *    └── NotificationPane — always on top, transparent overlay
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("PMS starting...");

        // 1. Init local database
        DatabaseConfig.initLocal();

        // 2. Build root layout
        StackPane contentPane = new StackPane();
        NotificationPane notifPane = new NotificationPane();
        StackPane root = new StackPane(contentPane, notifPane);

        // 3. Wire up utilities
        Navigator.init(contentPane, primaryStage);
        Notifier.init(notifPane);

        // 4. Start background sync (will connect to remote if credentials exist)
        SyncManager.getInstance().startAutoSync();

        // 5. Route: setup wizard if no admin, otherwise login
        try {
            UserDAO userDAO = new UserDAO();
            if (!userDAO.hasAnyAdmin()) {
                logger.info("No admin found — starting setup wizard at Welcome.");
                Navigator.navigateTo("/fxml/setup/Welcome.fxml");
            } else {
                Navigator.navigateTo("/fxml/Login.fxml");
            }
        } catch (Exception e) {
            logger.error("Startup routing failed", e);
            Navigator.navigateTo("/fxml/Login.fxml");
        }

        // 6. Show window
        Scene scene = new Scene(root, 900, 600);
        com.pms.util.UIUtil.enableEnterToClick(scene);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(520);
        primaryStage.setMinHeight(460);
        primaryStage.centerOnScreen();
        primaryStage.show();

        logger.info("PMS ready.");
    }

    @Override
    public void stop() {
        logger.info("PMS shutting down...");
        SyncManager.getInstance().shutdown();
        DatabaseConfig.closeAll();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

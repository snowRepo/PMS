package com.pms;

import com.pms.config.DatabaseConfig;
import com.pms.dao.UserDAO;
import com.pms.sync.SyncManager;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import com.pms.util.NotificationPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
        com.pms.util.BarcodeScannerManager.getInstance().attachToScene(scene);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        primaryStage.setScene(scene);
        
        // ── Cross-platform icon rendering ────────────────────────────────────────
        // Two dedicated icon sets are pre-generated:
        //   icon_macos_NxN.png  — squircle-masked PNGs (transparent corners)
        //                         matches native macOS Dock appearance exactly
        //   icon_win_NxN.png    — square, solid-background PNGs
        //                         matches Windows 10/11 & Linux taskbar appearance
        String os = System.getProperty("os.name", "generic").toLowerCase(java.util.Locale.ENGLISH);
        if (os.contains("mac") || os.contains("darwin")) {
            // macOS: push the 1024-px squircle image to the Dock via AWT Taskbar.
            // We deliberately skip stage.getIcons() — JavaFX would place a raw
            // square icon in the title bar, which looks non-native on macOS.
            try {
                java.awt.Image dockIcon = javax.imageio.ImageIO.read(
                        getClass().getResource("/images/icon_macos_1024x1024.png"));
                if (java.awt.Taskbar.isTaskbarSupported()) {
                    java.awt.Taskbar.getTaskbar().setIconImage(dockIcon);
                    logger.info("macOS Dock icon set (squircle 1024 px Retina).");
                }
            } catch (Exception e) {
                logger.warn("Failed to set macOS Dock icon", e);
            }
        } else {
            // Windows & Linux: load the full square size ladder so the OS always
            // picks the sharpest match — 16/32 for title bar, 48 for taskbar,
            // 128/256 for jump-list / large icon view.
            int[] sizes = {16, 32, 48, 64, 128, 256};
            try {
                for (int size : sizes) {
                    java.io.InputStream is = getClass()
                            .getResourceAsStream("/images/icon_win_" + size + "x" + size + ".png");
                    if (is != null) {
                        primaryStage.getIcons().add(new Image(is));
                    }
                }
                logger.info("Window/taskbar icons loaded ({} sizes).", sizes.length);
            } catch (Exception e) {
                logger.warn("Failed to load application icons", e);
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

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

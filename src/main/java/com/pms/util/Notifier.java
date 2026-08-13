package com.pms.util;

/**
 * Static facade for showing toast notifications from any controller.
 *
 * Initialize once in Main.java:
 *   Notifier.init(notificationPane);
 *
 * Then call from anywhere:
 *   Notifier.success("Record saved.");
 *   Notifier.error("Failed to connect.");
 *   Notifier.warning("Stock is low.");
 *   Notifier.info("Sync in progress...");
 */
public final class Notifier {

    private static NotificationPane pane;

    private Notifier() {}

    public static void init(NotificationPane notificationPane) {
        pane = notificationPane;
    }

    public static void success(String message) {
        show(message, NotificationType.SUCCESS);
    }

    public static void error(String message) {
        show(message, NotificationType.ERROR);
    }

    public static void warning(String message) {
        show(message, NotificationType.WARNING);
    }

    public static void info(String message) {
        show(message, NotificationType.INFO);
    }

    private static void show(String message, NotificationType type) {
        if (pane == null) return;
        pane.show(message, type);
    }
}

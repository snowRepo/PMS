package com.pms.util;

/**
 * Types of toast notifications shown to the user.
 */
public enum NotificationType {
    SUCCESS("✓"),
    ERROR("✕"),
    WARNING("⚠"),
    INFO("ℹ");

    public final String icon;

    NotificationType(String icon) {
        this.icon = icon;
    }
}

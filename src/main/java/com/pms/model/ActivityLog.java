package com.pms.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActivityLog {

    private String id;
    private String userId;
    private String username;
    private String action;
    private String description;
    private String createdAt;
    private boolean synced;

    public ActivityLog() {
    }

    public ActivityLog(String id, String userId, String username, String action, String description, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }
    
    public String getFormattedDate() {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            LocalDateTime dt = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return dt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"));
        } catch (Exception e) {
            return createdAt;
        }
    }
}

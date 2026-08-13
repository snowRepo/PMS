package com.pms.model;

/**
 * Represents a staff member / system user.
 *
 * Roles:
 *  - admin       : full access
 *  - pharmacist  : inventory + sales + reports
 *  - cashier     : sales only
 */
public class User {

    private String id;
    private String username;
    private String password;           // BCrypt hash
    private String role;
    private String fullName;
    private boolean active;
    private boolean tempPassword;      // true = must change on next login
    private String lastPasswordChange; // ISO timestamp for 30-day check
    private String createdAt;
    private String updatedAt;

    public User() {}

    public User(String id, String username, String password, String role,
                String fullName, boolean active, String createdAt, String updatedAt) {
        this.id        = id;
        this.username  = username;
        this.password  = password;
        this.role      = role;
        this.fullName  = fullName;
        this.active    = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()               { return id; }
    public void setId(String id)        { this.id = id; }

    public String getUsername()                    { return username; }
    public void setUsername(String username)        { this.username = username; }

    public String getPassword()                    { return password; }
    public void setPassword(String password)        { this.password = password; }

    public String getRole()                        { return role; }
    public void setRole(String role)               { this.role = role; }

    public String getFullName()                    { return fullName; }
    public void setFullName(String fullName)        { this.fullName = fullName; }

    public boolean isActive()                      { return active; }
    public void setActive(boolean active)           { this.active = active; }

    public String getCreatedAt()                   { return createdAt; }
    public void setCreatedAt(String createdAt)      { this.createdAt = createdAt; }

    public String getUpdatedAt()                   { return updatedAt; }
    public void setUpdatedAt(String updatedAt)      { this.updatedAt = updatedAt; }

    public boolean isTempPassword()                    { return tempPassword; }
    public void setTempPassword(boolean tempPassword)  { this.tempPassword = tempPassword; }

    public String getLastPasswordChange()              { return lastPasswordChange; }
    public void setLastPasswordChange(String v)        { this.lastPasswordChange = v; }

    @Override
    public String toString() {
        return fullName + " (" + role + ")";
    }
}

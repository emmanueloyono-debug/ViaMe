package com.viame.model;

import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * Abstract domain supertype representing any system actor in ViaMe.
 * Implements inheritance, encapsulation, and common identity attributes.
 */
public abstract class User {
    protected long id;
    protected String email;
    protected String phone;
    protected String passwordHash;
    protected String passwordSalt;
    protected UserRole role;
    protected UserStatus status;
    protected LocalDateTime createdAt;

    public User(long id, String email, String phone, String passwordHash, String passwordSalt, UserRole role, UserStatus status) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

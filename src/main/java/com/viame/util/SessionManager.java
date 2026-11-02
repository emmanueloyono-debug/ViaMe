package com.viame.util;

import com.viame.model.User;
import com.viame.model.enums.UserRole;

import java.util.Optional;

/**
 * Thread-safe Singleton holding the currently authenticated user of the desktop session.
 */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private volatile User currentUser;

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized void login(User user) {
        this.currentUser = user;
    }

    public synchronized void logout() {
        this.currentUser = null;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public boolean isAuthenticated() {
        return currentUser != null;
    }

    public boolean hasRole(UserRole role) {
        User user = currentUser;
        return user != null && user.getRole() == role;
    }

    /**
     * Guards role-restricted operations (RBAC).
     *
     * @throws SecurityException if nobody is logged in or the role does not match
     */
    public User requireRole(UserRole role) {
        User user = currentUser;
        if (user == null) {
            throw new SecurityException("Authentication required");
        }
        if (user.getRole() != role) {
            throw new SecurityException("Access denied: " + role + " role required");
        }
        return user;
    }
}


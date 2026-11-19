package com.viame.util;

import com.viame.model.User;
import com.viame.model.enums.UserRole;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, multi-session Session Manager supporting isolated session states.
 *
 * <p>Supports:
 * <ul>
 *   <li>Per-thread session isolation via {@link ThreadLocal} so concurrent operations (e.g. Passenger and Driver) do not collide.</li>
 *   <li>Per-token session isolation via a concurrent registry for REST / API callers.</li>
 *   <li>Independent instance creation via {@link #createIsolated()} for modular testing.</li>
 *   <li>Scoped execution via {@link #withUser(User, Callable)} and {@link #runWithUser(User, Runnable)}.</li>
 *   <li>Full backward compatibility with legacy global singleton callers via {@link #getInstance()}.</li>
 * </ul>
 * </p>
 */
public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private final ThreadLocal<User> threadUser = new ThreadLocal<>();
    private final Map<String, User> tokenSessions = new ConcurrentHashMap<>();
    private volatile User globalUser;

    public SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Creates an independent, isolated {@link SessionManager} instance.
     */
    public static SessionManager createIsolated() {
        return new SessionManager();
    }

    // --- Current Thread / Singleton API (Backward Compatible & Thread-Isolated) ---

    public synchronized void login(User user) {
        this.threadUser.set(user);
        this.globalUser = user;
    }

    public synchronized void logout() {
        this.threadUser.remove();
        this.globalUser = null;
    }

    public Optional<User> getCurrentUser() {
        User user = threadUser.get();
        if (user == null) {
            user = globalUser;
        }
        return Optional.ofNullable(user);
    }

    public boolean isAuthenticated() {
        return getCurrentUser().isPresent();
    }

    public boolean hasRole(UserRole role) {
        return getCurrentUser().map(u -> u.getRole() == role).orElse(false);
    }

    /**
     * Guards role-restricted operations (RBAC).
     *
     * @throws SecurityException if nobody is logged in or the role does not match
     */
    public User requireRole(UserRole role) {
        User user = getCurrentUser().orElseThrow(() -> new SecurityException("Authentication required"));
        if (user.getRole() != role) {
            throw new SecurityException("Access denied: " + role + " role required");
        }
        return user;
    }

    // --- Per-Token Session Isolation API ---

    public void login(String sessionToken, User user) {
        if (sessionToken != null && user != null) {
            tokenSessions.put(sessionToken, user);
            threadUser.set(user);
        }
    }

    public void logout(String sessionToken) {
        if (sessionToken != null) {
            User removed = tokenSessions.remove(sessionToken);
            if (removed != null && threadUser.get() == removed) {
                threadUser.remove();
            }
        }
    }

    public Optional<User> getCurrentUser(String sessionToken) {
        if (sessionToken == null) return Optional.empty();
        return Optional.ofNullable(tokenSessions.get(sessionToken));
    }

    public boolean isAuthenticated(String sessionToken) {
        return sessionToken != null && tokenSessions.containsKey(sessionToken);
    }

    public boolean hasRole(String sessionToken, UserRole role) {
        return getCurrentUser(sessionToken).map(u -> u.getRole() == role).orElse(false);
    }

    public User requireRole(String sessionToken, UserRole role) {
        User user = getCurrentUser(sessionToken)
                .orElseThrow(() -> new SecurityException("Authentication required for token"));
        if (user.getRole() != role) {
            throw new SecurityException("Access denied: " + role + " role required");
        }
        return user;
    }

    public void bindCurrentThread(String sessionToken) {
        if (sessionToken != null) {
            User u = tokenSessions.get(sessionToken);
            if (u != null) {
                threadUser.set(u);
            }
        }
    }

    public void unbindCurrentThread() {
        threadUser.remove();
    }

    public void clearAllSessions() {
        tokenSessions.clear();
        threadUser.remove();
        globalUser = null;
    }

    // --- Scoped Execution API ---

    /**
     * Executes a task within the isolated session context of the specified user.
     */
    public <T> T withUser(User user, Callable<T> task) throws Exception {
        User previous = threadUser.get();
        threadUser.set(user);
        try {
            return task.call();
        } finally {
            if (previous != null) {
                threadUser.set(previous);
            } else {
                threadUser.remove();
            }
        }
    }

    /**
     * Runs an action within the isolated session context of the specified user.
     */
    public void runWithUser(User user, Runnable task) {
        User previous = threadUser.get();
        threadUser.set(user);
        try {
            task.run();
        } finally {
            if (previous != null) {
                threadUser.set(previous);
            } else {
                threadUser.remove();
            }
        }
    }
}

package com.viame.dao;

import com.viame.dao.impl.SessionDAOImpl;
import com.viame.model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionDAO Database Integration Tests")
class SessionDAOTest {

    private SessionDAO sessionDAO;
    // Exactly 64 hex characters (SHA-256 hash length)
    private final String testTokenHash = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private final long testUserId = 4L; // Seed passenger Alice

    @BeforeEach
    void setUp() {
        sessionDAO = new SessionDAOImpl();
        sessionDAO.deleteByTokenHash(testTokenHash);
    }

    @AfterEach
    void tearDown() {
        sessionDAO.deleteByTokenHash(testTokenHash);
    }

    @Test
    @DisplayName("Saves, retrieves, updates and deletes session records in MySQL session table")
    void testSessionLifecycle() {
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Session session = new Session(testTokenHash, testUserId, expiresAt, "127.0.0.1");

        // 1. Save session
        Session saved = sessionDAO.save(session);
        assertNotNull(saved);
        assertEquals(testTokenHash, saved.getTokenHash());

        // 2. Find by token hash
        Optional<Session> retrieved = sessionDAO.findByTokenHash(testTokenHash);
        assertTrue(retrieved.isPresent(), "Session should be present in session table");
        assertEquals(testUserId, retrieved.get().getUserId());
        assertEquals("127.0.0.1", retrieved.get().getIpAddress());
        assertFalse(retrieved.get().isExpired());

        // 3. Update session
        Instant newExpiry = expiresAt.plus(1, ChronoUnit.DAYS);
        retrieved.get().setExpiresAt(newExpiry);
        retrieved.get().setIpAddress("192.168.1.100");
        boolean updated = sessionDAO.update(retrieved.get());
        assertTrue(updated, "Session update should succeed");

        Optional<Session> reRetrieved = sessionDAO.findByTokenHash(testTokenHash);
        assertTrue(reRetrieved.isPresent());
        assertEquals("192.168.1.100", reRetrieved.get().getIpAddress());

        // 4. Delete by token hash
        boolean deleted = sessionDAO.deleteByTokenHash(testTokenHash);
        assertTrue(deleted, "Session delete should succeed");
        assertTrue(sessionDAO.findByTokenHash(testTokenHash).isEmpty());
    }

    @Test
    @DisplayName("Purges expired sessions correctly")
    void testDeleteExpiredSessions() {
        String expiredHash = "e1e2e3e4e5e60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
        Session expired = new Session(expiredHash, testUserId, Instant.now().minus(2, ChronoUnit.HOURS), "127.0.0.1");
        sessionDAO.save(expired);

        int purged = sessionDAO.deleteExpired();
        assertTrue(purged >= 1, "At least one expired session should be deleted");
        assertTrue(sessionDAO.findByTokenHash(expiredHash).isEmpty());
    }
}

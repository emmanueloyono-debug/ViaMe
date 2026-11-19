package com.viame.dao;

import com.viame.dao.impl.AuditLogDAOImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditLogDAO Database Integration Tests")
class AuditLogDAOTest {

    private AuditLogDAO auditLogDAO;
    private final String actionTag = "TEST_AUDIT_" + System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        auditLogDAO = new AuditLogDAOImpl();
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM audit_log WHERE action LIKE 'TEST_AUDIT_%'")) {
            stmt.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Logs audit entries directly and in transaction with null and non-null fields")
    void testAuditLogging() throws Exception {
        // 1. Log with standalone connection
        auditLogDAO.log(1L, actionTag + "_1", "test_entity", 100L);

        // 2. Log with transaction connection and nulls
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            auditLogDAO.log(conn, null, actionTag + "_2", null, null);
        }

        // Verify entries in audit_log table
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT user_id, action, entity, entity_id FROM audit_log WHERE action LIKE ? ORDER BY id ASC")) {
            stmt.setString(1, actionTag + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "First log entry must exist");
                assertEquals(1L, rs.getLong("user_id"));
                assertEquals(actionTag + "_1", rs.getString("action"));
                assertEquals("test_entity", rs.getString("entity"));
                assertEquals(100L, rs.getLong("entity_id"));

                assertTrue(rs.next(), "Second log entry must exist");
                assertNull(rs.getObject("user_id"));
                assertEquals(actionTag + "_2", rs.getString("action"));
                assertNull(rs.getString("entity"));
                assertNull(rs.getObject("entity_id"));

                assertFalse(rs.next());
            }
        }
    }
}

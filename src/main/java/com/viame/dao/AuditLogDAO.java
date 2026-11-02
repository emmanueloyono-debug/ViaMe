package com.viame.dao;

import java.sql.Connection;
import java.sql.SQLException;

/** Append-only security &amp; regulatory event trail ({@code audit_log}). */
public interface AuditLogDAO {
    void log(Long userId, String action, String entity, Long entityId);
    void log(Connection conn, Long userId, String action, String entity, Long entityId) throws SQLException;
}


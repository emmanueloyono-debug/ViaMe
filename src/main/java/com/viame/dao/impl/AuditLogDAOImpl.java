package com.viame.dao.impl;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.DBConnection;
import com.viame.exception.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class AuditLogDAOImpl implements AuditLogDAO {

    @Override
    public void log(Long userId, String action, String entity, Long entityId) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            log(conn, userId, action, entity, entityId);
        } catch (SQLException e) {
            throw new DatabaseException("Error writing audit log: " + action, e);
        }
    }

    @Override
    public void log(Connection conn, Long userId, String action, String entity, Long entityId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO audit_log (user_id, action, entity, entity_id) VALUES (?, ?, ?, ?)")) {
            if (userId != null) stmt.setLong(1, userId); else stmt.setNull(1, Types.BIGINT);
            stmt.setString(2, action);
            if (entity != null) stmt.setString(3, entity); else stmt.setNull(3, Types.VARCHAR);
            if (entityId != null) stmt.setLong(4, entityId); else stmt.setNull(4, Types.BIGINT);
            stmt.executeUpdate();
        }
    }
}


package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.SessionDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link SessionDAO} for persisting REST session tokens in MySQL.
 */
public class SessionDAOImpl implements SessionDAO {

    @Override
    public Optional<Session> findById(String tokenHash) {
        return findByTokenHash(tokenHash);
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT token_hash, user_id, expires_at, ip_address, created_at FROM session WHERE token_hash = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error finding session by token hash: " + tokenHash, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Session> findAll() {
        String sql = "SELECT token_hash, user_id, expires_at, ip_address, created_at FROM session ORDER BY created_at DESC";
        List<Session> sessions = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                sessions.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving all sessions", e);
        }
        return sessions;
    }

    @Override
    public Session save(Session session) {
        if (session == null || session.getTokenHash() == null) {
            throw new IllegalArgumentException("Session and tokenHash cannot be null");
        }
        String sql = "INSERT INTO session (token_hash, user_id, expires_at, ip_address, created_at) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), ip_address = VALUES(ip_address)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.getTokenHash());
            stmt.setLong(2, session.getUserId());
            stmt.setTimestamp(3, session.getExpiresAt() != null ? Timestamp.from(session.getExpiresAt()) : null);
            stmt.setString(4, session.getIpAddress());
            stmt.setTimestamp(5, session.getCreatedAt() != null ? Timestamp.from(session.getCreatedAt()) : new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            return session;
        } catch (SQLException e) {
            throw new DatabaseException("Error saving session for user: " + session.getUserId(), e);
        }
    }

    @Override
    public boolean update(Session session) {
        if (session == null || session.getTokenHash() == null) {
            return false;
        }
        String sql = "UPDATE session SET expires_at = ?, ip_address = ? WHERE token_hash = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, session.getExpiresAt() != null ? Timestamp.from(session.getExpiresAt()) : null);
            stmt.setString(2, session.getIpAddress());
            stmt.setString(3, session.getTokenHash());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error updating session: " + session.getTokenHash(), e);
        }
    }

    @Override
    public boolean delete(String tokenHash) {
        return deleteByTokenHash(tokenHash);
    }

    @Override
    public boolean deleteByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return false;
        }
        String sql = "DELETE FROM session WHERE token_hash = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenHash);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting session by token hash: " + tokenHash, e);
        }
    }

    @Override
    public boolean deleteByUserId(long userId) {
        String sql = "DELETE FROM session WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting sessions for user: " + userId, e);
        }
    }

    @Override
    public int deleteExpired() {
        String sql = "DELETE FROM session WHERE expires_at < CURRENT_TIMESTAMP";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting expired sessions", e);
        }
    }

    private Session mapRow(ResultSet rs) throws SQLException {
        Timestamp exp = rs.getTimestamp("expires_at");
        Timestamp crt = rs.getTimestamp("created_at");
        return new Session(
                rs.getString("token_hash"),
                rs.getLong("user_id"),
                exp != null ? exp.toInstant() : null,
                rs.getString("ip_address"),
                crt != null ? crt.toInstant() : null
        );
    }
}

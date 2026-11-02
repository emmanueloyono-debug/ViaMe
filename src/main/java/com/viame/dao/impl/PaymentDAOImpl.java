package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.PaymentDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Payment;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.PaymentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

public class PaymentDAOImpl implements PaymentDAO {

    @Override
    public Payment insert(Connection conn, Payment payment) throws SQLException {
        String sql = "INSERT INTO payment (ride_id, amount, method, status, transaction_ref, paid_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindCommon(stmt, payment);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    payment.setId(keys.getLong(1));
                }
            }
            return payment;
        }
    }

    @Override
    public Optional<Payment> findByRideId(long rideId) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return find(conn, "SELECT * FROM payment WHERE ride_id = ? ORDER BY id DESC LIMIT 1", rideId);
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving payment for ride: " + rideId, e);
        }
    }

    @Override
    public Optional<Payment> findByRideIdForUpdate(Connection conn, long rideId) throws SQLException {
        return find(conn, "SELECT * FROM payment WHERE ride_id = ? ORDER BY id DESC LIMIT 1 FOR UPDATE", rideId);
    }

    @Override
    public boolean update(Connection conn, Payment payment) throws SQLException {
        String sql = "UPDATE payment SET ride_id = ?, amount = ?, method = ?, status = ?, transaction_ref = ?, paid_at = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindCommon(stmt, payment);
            stmt.setLong(7, payment.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteByRideId(Connection conn, long rideId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payment WHERE ride_id = ?")) {
            stmt.setLong(1, rideId);
            return stmt.executeUpdate() > 0;
        }
    }

    private void bindCommon(PreparedStatement stmt, Payment payment) throws SQLException {
        stmt.setLong(1, payment.getRideId());
        stmt.setBigDecimal(2, payment.getAmount());
        stmt.setString(3, payment.getMethod().name());
        stmt.setString(4, payment.getStatus().name());
        if (payment.getTransactionRef() != null) stmt.setString(5, payment.getTransactionRef()); else stmt.setNull(5, Types.VARCHAR);
        if (payment.getPaidAt() != null) stmt.setTimestamp(6, Timestamp.valueOf(payment.getPaidAt())); else stmt.setNull(6, Types.TIMESTAMP);
    }

    private Optional<Payment> find(Connection conn, String sql, long rideId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, rideId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp paidAt = rs.getTimestamp("paid_at");
                return Optional.of(new Payment(
                        rs.getLong("id"),
                        rs.getLong("ride_id"),
                        rs.getBigDecimal("amount"),
                        PaymentMethod.valueOf(rs.getString("method")),
                        PaymentStatus.valueOf(rs.getString("status")),
                        rs.getString("transaction_ref"),
                        paidAt != null ? paidAt.toLocalDateTime() : null));
            }
        }
    }
}


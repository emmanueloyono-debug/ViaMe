package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.PassengerDAO;
import com.viame.exception.DatabaseException;
import com.viame.exception.EntityNotFoundException;
import com.viame.model.Passenger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PassengerDAOImpl implements PassengerDAO {

    @Override
    public Optional<Passenger> findById(long passengerId) {
        String sql = UserRowMapper.BASE_SELECT + " WHERE u.id = ? AND u.role = 'PASSENGER'";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, passengerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of((Passenger) UserRowMapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving passenger: " + passengerId, e);
        }
    }

    @Override
    public BigDecimal getWalletBalance(Connection conn, long passengerId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT wallet_balance FROM passenger_profile WHERE user_id = ?")) {
            stmt.setLong(1, passengerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new EntityNotFoundException("Passenger", passengerId);
                }
                return rs.getBigDecimal(1);
            }
        }
    }

    @Override
    public void creditWallet(Connection conn, long passengerId, BigDecimal amount) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE passenger_profile SET wallet_balance = wallet_balance + ? WHERE user_id = ?")) {
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, passengerId);
            if (stmt.executeUpdate() == 0) {
                throw new EntityNotFoundException("Passenger", passengerId);
            }
        }
    }

    @Override
    public boolean debitWallet(Connection conn, long passengerId, BigDecimal amount) throws SQLException {
        // Conditional update = atomic check-and-debit; no separate SELECT, no race window
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE passenger_profile SET wallet_balance = wallet_balance - ? WHERE user_id = ? AND wallet_balance >= ?")) {
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, passengerId);
            stmt.setBigDecimal(3, amount);
            return stmt.executeUpdate() > 0;
        }
    }
}


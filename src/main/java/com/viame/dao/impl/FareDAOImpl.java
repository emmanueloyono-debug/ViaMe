package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.FareDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Fare;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class FareDAOImpl implements FareDAO {

    @Override
    public Fare insert(Connection conn, Fare fare) throws SQLException {
        String sql = """
            INSERT INTO fare (ride_id, base_fare, distance_component, time_component,
                              surge_multiplier, total_amount, currency)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, fare.getRideId());
            stmt.setBigDecimal(2, fare.getBaseFare());
            stmt.setBigDecimal(3, fare.getDistanceComponent());
            stmt.setBigDecimal(4, fare.getTimeComponent());
            stmt.setBigDecimal(5, fare.getSurgeMultiplier());
            stmt.setBigDecimal(6, fare.getTotalAmount());
            stmt.setString(7, fare.getCurrency());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    fare.setId(keys.getLong(1));
                }
            }
            return fare;
        }
    }

    @Override
    public Optional<Fare> findByRideId(long rideId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM fare WHERE ride_id = ?")) {
            stmt.setLong(1, rideId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Fare(
                        rs.getLong("id"),
                        rs.getLong("ride_id"),
                        rs.getBigDecimal("base_fare"),
                        rs.getBigDecimal("distance_component"),
                        rs.getBigDecimal("time_component"),
                        rs.getBigDecimal("surge_multiplier"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("currency")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving fare for ride: " + rideId, e);
        }
    }
}


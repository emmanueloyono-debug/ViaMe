package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.DriverDAO;
import com.viame.exception.DatabaseException;
import com.viame.exception.EntityNotFoundException;
import com.viame.model.Driver;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DriverDAOImpl implements DriverDAO {

    @Override
    public Optional<Driver> findById(long driverId) {
        String sql = UserRowMapper.BASE_SELECT + " WHERE u.id = ? AND u.role = 'DRIVER'";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, driverId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of((Driver) UserRowMapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving driver: " + driverId, e);
        }
    }

    @Override
    public List<Driver> findAvailableInBoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
        String sql = UserRowMapper.BASE_SELECT + """
             JOIN vehicle v ON v.driver_id = u.id
            WHERE u.role = 'DRIVER' AND u.status = 'ACTIVE'
              AND d.verification_status = 'APPROVED'
              AND d.availability = 'ONLINE'
              AND v.status = 'ACTIVE'
              AND d.current_lat BETWEEN ? AND ?
              AND d.current_lng BETWEEN ? AND ?
            """;
        List<Driver> drivers = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, minLat);
            stmt.setDouble(2, maxLat);
            stmt.setDouble(3, minLng);
            stmt.setDouble(4, maxLng);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    drivers.add((Driver) UserRowMapper.map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error searching available drivers", e);
        }
        return drivers;
    }

    @Override
    public List<Driver> findByVerificationStatus(DriverVerificationStatus status) {
        String sql = UserRowMapper.BASE_SELECT + " WHERE u.role = 'DRIVER' AND d.verification_status = ? ORDER BY u.created_at";
        List<Driver> drivers = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    drivers.add((Driver) UserRowMapper.map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listing drivers by verification status", e);
        }
        return drivers;
    }

    @Override
    public int countOnlineDrivers() {
        String sql = "SELECT COUNT(*) FROM driver_profile WHERE availability = 'ONLINE' AND verification_status = 'APPROVED'";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error counting online drivers", e);
        }
    }

    @Override
    public boolean updateAvailability(long driverId, DriverAvailability availability) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            updateAvailability(conn, driverId, availability);
            return true;
        } catch (EntityNotFoundException e) {
            return false;
        } catch (SQLException e) {
            throw new DatabaseException("Error updating driver availability: " + driverId, e);
        }
    }

    @Override
    public void updateAvailability(Connection conn, long driverId, DriverAvailability availability) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE driver_profile SET availability = ? WHERE user_id = ?")) {
            stmt.setString(1, availability.name());
            stmt.setLong(2, driverId);
            if (stmt.executeUpdate() == 0) {
                throw new EntityNotFoundException("Driver", driverId);
            }
        }
    }

    @Override
    public boolean updateLocation(long driverId, double lat, double lng) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE driver_profile SET current_lat = ?, current_lng = ? WHERE user_id = ?")) {
            stmt.setDouble(1, lat);
            stmt.setDouble(2, lng);
            stmt.setLong(3, driverId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error updating driver location: " + driverId, e);
        }
    }

    @Override
    public void creditEarnings(Connection conn, long driverId, BigDecimal amount) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE driver_profile SET total_earnings = total_earnings + ? WHERE user_id = ?")) {
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, driverId);
            if (stmt.executeUpdate() == 0) {
                throw new EntityNotFoundException("Driver", driverId);
            }
        }
    }

    @Override
    public boolean updateVerificationStatus(Connection conn, long driverId, DriverVerificationStatus status) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE driver_profile SET verification_status = ? WHERE user_id = ?")) {
            stmt.setString(1, status.name());
            stmt.setLong(2, driverId);
            return stmt.executeUpdate() > 0;
        }
    }
}


package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.TripDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Trip;
import com.viame.model.enums.TripStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TripDAOImpl implements TripDAO {

    @Override
    public Optional<Trip> findById(Long id) {
        String sql = "SELECT * FROM ride WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToTrip(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving ride by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Trip> findAll() {
        String sql = "SELECT * FROM ride ORDER BY requested_at DESC";
        List<Trip> trips = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                trips.add(mapResultSetToTrip(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listing all rides", e);
        }
        return trips;
    }

    @Override
    public Trip save(Trip trip) {
        String sql = """
            INSERT INTO ride (passenger_id, driver_id, vehicle_id, pricing_rule_id,
                              pickup_lat, pickup_lng, pickup_address,
                              dropoff_lat, dropoff_lng, dropoff_address,
                              distance_km, duration_min, status, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, trip.getPassengerId());
            if (trip.getDriverId() != null) stmt.setLong(2, trip.getDriverId()); else stmt.setNull(2, Types.BIGINT);
            if (trip.getVehicleId() != null) stmt.setLong(3, trip.getVehicleId()); else stmt.setNull(3, Types.BIGINT);
            stmt.setLong(4, trip.getPricingRuleId());
            stmt.setDouble(5, trip.getPickupLat());
            stmt.setDouble(6, trip.getPickupLng());
            stmt.setString(7, trip.getPickupAddress());
            stmt.setDouble(8, trip.getDropoffLat());
            stmt.setDouble(9, trip.getDropoffLng());
            stmt.setString(10, trip.getDropoffAddress());
            stmt.setBigDecimal(11, trip.getDistanceKm());
            stmt.setInt(12, trip.getDurationMin());
            stmt.setString(13, trip.getStatus().name());
            stmt.setInt(14, trip.getVersion());

            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    trip.setId(generatedKeys.getLong(1));
                }
            }
            return trip;
        } catch (SQLException e) {
            throw new DatabaseException("Error persisting new trip", e);
        }
    }

    @Override
    public boolean update(Trip trip) {
        String sql = """
            UPDATE ride SET driver_id = ?, vehicle_id = ?, status = ?,
                            accepted_at = ?, started_at = ?, completed_at = ?, cancelled_at = ?,
                            version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (trip.getDriverId() != null) stmt.setLong(1, trip.getDriverId()); else stmt.setNull(1, Types.BIGINT);
            if (trip.getVehicleId() != null) stmt.setLong(2, trip.getVehicleId()); else stmt.setNull(2, Types.BIGINT);
            stmt.setString(3, trip.getStatus().name());
            stmt.setTimestamp(4, trip.getAcceptedAt() != null ? Timestamp.valueOf(trip.getAcceptedAt()) : null);
            stmt.setTimestamp(5, trip.getStartedAt() != null ? Timestamp.valueOf(trip.getStartedAt()) : null);
            stmt.setTimestamp(6, trip.getCompletedAt() != null ? Timestamp.valueOf(trip.getCompletedAt()) : null);
            stmt.setTimestamp(7, trip.getCancelledAt() != null ? Timestamp.valueOf(trip.getCancelledAt()) : null);
            stmt.setLong(8, trip.getId());
            stmt.setInt(9, trip.getVersion());

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                trip.setVersion(trip.getVersion() + 1);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new DatabaseException("Error updating trip with id: " + trip.getId(), e);
        }
    }

    @Override
    public boolean delete(Long id) {
        String sql = "DELETE FROM ride WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting trip: " + id, e);
        }
    }

    @Override
    public List<Trip> findByPassengerId(long passengerId) {
        String sql = "SELECT * FROM ride WHERE passenger_id = ? ORDER BY requested_at DESC";
        List<Trip> trips = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, passengerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trips.add(mapResultSetToTrip(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error fetching passenger trips: " + passengerId, e);
        }
        return trips;
    }

    @Override
    public List<Trip> findByDriverId(long driverId) {
        String sql = "SELECT * FROM ride WHERE driver_id = ? ORDER BY requested_at DESC";
        List<Trip> trips = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, driverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trips.add(mapResultSetToTrip(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error fetching driver trips: " + driverId, e);
        }
        return trips;
    }

    @Override
    public List<Trip> findByStatus(TripStatus status) {
        String sql = "SELECT * FROM ride WHERE status = ? ORDER BY requested_at DESC";
        List<Trip> trips = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trips.add(mapResultSetToTrip(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error fetching trips by status: " + status, e);
        }
        return trips;
    }

    @Override
    public boolean lockAndAssignTrip(long tripId, long driverId, long vehicleId) {
        String selectLock = "SELECT status, version FROM ride WHERE id = ? FOR UPDATE";
        String updateTrip = "UPDATE ride SET status = 'ASSIGNED', driver_id = ?, vehicle_id = ?, accepted_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ?";
        String updateDriver = "UPDATE driver_profile SET availability = 'ON_TRIP' WHERE user_id = ?";

        Connection conn = DBConnection.getInstance().getConnection();
        try {
            conn.setAutoCommit(false); // Begin ACID transaction

            try (PreparedStatement lockStmt = conn.prepareStatement(selectLock)) {
                lockStmt.setLong(1, tripId);
                try (ResultSet rs = lockStmt.executeQuery()) {
                    if (!rs.next() || !TripStatus.REQUESTED.name().equals(rs.getString("status"))) {
                        conn.rollback();
                        return false; // Trip was already accepted by another driver or cancelled
                    }
                }
            }

            try (PreparedStatement tripStmt = conn.prepareStatement(updateTrip);
                 PreparedStatement drvStmt = conn.prepareStatement(updateDriver)) {
                tripStmt.setLong(1, driverId);
                tripStmt.setLong(2, vehicleId);
                tripStmt.setLong(3, tripId);
                tripStmt.executeUpdate();

                drvStmt.setLong(1, driverId);
                drvStmt.executeUpdate();

                conn.commit();
                return true;
            }
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new DatabaseException("Transaction failed: Lock and assign trip " + tripId, e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public boolean updateTripStatus(long tripId, TripStatus newStatus, int expectedVersion) {
        String sql = "UPDATE ride SET status = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus.name());
            stmt.setLong(2, tripId);
            stmt.setInt(3, expectedVersion);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update status for trip: " + tripId, e);
        }
    }

    private Trip mapResultSetToTrip(ResultSet rs) throws SQLException {
        Trip trip = new Trip(
            rs.getLong("id"),
            rs.getLong("passenger_id"),
            rs.getObject("driver_id") != null ? rs.getLong("driver_id") : null,
            rs.getObject("vehicle_id") != null ? rs.getLong("vehicle_id") : null,
            rs.getLong("pricing_rule_id"),
            rs.getDouble("pickup_lat"),
            rs.getDouble("pickup_lng"),
            rs.getString("pickup_address"),
            rs.getDouble("dropoff_lat"),
            rs.getDouble("dropoff_lng"),
            rs.getString("dropoff_address"),
            rs.getBigDecimal("distance_km"),
            rs.getInt("duration_min"),
            TripStatus.valueOf(rs.getString("status")),
            rs.getInt("version")
        );
        Timestamp req = rs.getTimestamp("requested_at");
        if (req != null) trip.setRequestedAt(req.toLocalDateTime());
        Timestamp acc = rs.getTimestamp("accepted_at");
        if (acc != null) trip.setAcceptedAt(acc.toLocalDateTime());
        Timestamp st = rs.getTimestamp("started_at");
        if (st != null) trip.setStartedAt(st.toLocalDateTime());
        Timestamp comp = rs.getTimestamp("completed_at");
        if (comp != null) trip.setCompletedAt(comp.toLocalDateTime());
        Timestamp canc = rs.getTimestamp("cancelled_at");
        if (canc != null) trip.setCancelledAt(canc.toLocalDateTime());
        return trip;
    }
}

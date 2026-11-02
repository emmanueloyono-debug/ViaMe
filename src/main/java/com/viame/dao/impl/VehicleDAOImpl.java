package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.VehicleDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Vehicle;
import com.viame.model.enums.VehicleStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VehicleDAOImpl implements VehicleDAO {

    @Override
    public Optional<Vehicle> findById(Long id) {
        return findOne("SELECT * FROM vehicle WHERE id = ?", id);
    }

    @Override
    public Optional<Vehicle> findByDriverId(long driverId) {
        return findOne("SELECT * FROM vehicle WHERE driver_id = ?", driverId);
    }

    @Override
    public List<Vehicle> findAll() {
        List<Vehicle> vehicles = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM vehicle ORDER BY id");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                vehicles.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listing vehicles", e);
        }
        return vehicles;
    }

    @Override
    public boolean existsByPlate(String plateNumber) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM vehicle WHERE plate_number = ?")) {
            stmt.setString(1, plateNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error checking plate number", e);
        }
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, vehicle);
        } catch (SQLException e) {
            throw new DatabaseException("Error saving vehicle " + vehicle.getPlateNumber(), e);
        }
    }

    @Override
    public Vehicle insert(Connection conn, Vehicle vehicle) throws SQLException {
        String sql = "INSERT INTO vehicle (driver_id, plate_number, make, model, seats, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, vehicle.getDriverId());
            stmt.setString(2, vehicle.getPlateNumber());
            stmt.setString(3, vehicle.getMake());
            stmt.setString(4, vehicle.getModel());
            stmt.setInt(5, vehicle.getSeats());
            stmt.setString(6, vehicle.getStatus().name());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    vehicle.setId(keys.getLong(1));
                }
            }
            return vehicle;
        }
    }

    @Override
    public boolean update(Vehicle vehicle) {
        String sql = "UPDATE vehicle SET plate_number = ?, make = ?, model = ?, seats = ?, status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vehicle.getPlateNumber());
            stmt.setString(2, vehicle.getMake());
            stmt.setString(3, vehicle.getModel());
            stmt.setInt(4, vehicle.getSeats());
            stmt.setString(5, vehicle.getStatus().name());
            stmt.setLong(6, vehicle.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error updating vehicle: " + vehicle.getId(), e);
        }
    }

    @Override
    public boolean updateStatusByDriver(Connection conn, long driverId, VehicleStatus status) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE vehicle SET status = ? WHERE driver_id = ?")) {
            stmt.setString(1, status.name());
            stmt.setLong(2, driverId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(Long id) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM vehicle WHERE id = ?")) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting vehicle: " + id, e);
        }
    }

    private Optional<Vehicle> findOne(String sql, long param) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving vehicle", e);
        }
    }

    private Vehicle map(ResultSet rs) throws SQLException {
        return new Vehicle(
                rs.getLong("id"),
                rs.getLong("driver_id"),
                rs.getString("plate_number"),
                rs.getString("make"),
                rs.getString("model"),
                rs.getInt("seats"),
                VehicleStatus.valueOf(rs.getString("status")));
    }
}


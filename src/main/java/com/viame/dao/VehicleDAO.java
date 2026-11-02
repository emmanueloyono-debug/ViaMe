package com.viame.dao;

import com.viame.model.Vehicle;
import com.viame.model.enums.VehicleStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface VehicleDAO extends GenericDAO<Vehicle, Long> {
    Optional<Vehicle> findByDriverId(long driverId);
    boolean existsByPlate(String plateNumber);
    Vehicle insert(Connection conn, Vehicle vehicle) throws SQLException;
    boolean updateStatusByDriver(Connection conn, long driverId, VehicleStatus status) throws SQLException;
}


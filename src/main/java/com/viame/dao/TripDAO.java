package com.viame.dao;

import com.viame.model.Trip;
import com.viame.model.enums.TripStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface TripDAO extends GenericDAO<Trip, Long> {
    List<Trip> findByPassengerId(long passengerId);
    List<Trip> findByDriverId(long driverId);
    List<Trip> findByStatus(TripStatus status);
    boolean lockAndAssignTrip(long tripId, long driverId, long vehicleId);
    boolean updateTripStatus(long tripId, TripStatus newStatus, int expectedVersion);

    /** Non-terminal trip (REQUESTED .. IN_PROGRESS) for the passenger, if any. */
    Optional<Trip> findActiveByPassengerId(long passengerId);
    int countByStatus(TripStatus status);

    Trip insert(Connection conn, Trip trip) throws SQLException;
    /** Optimistic-locked update inside a caller-managed transaction. */
    boolean update(Connection conn, Trip trip) throws SQLException;
}

package com.viame.dao;

import com.viame.model.Trip;
import com.viame.model.enums.TripStatus;

import java.util.List;

public interface TripDAO extends GenericDAO<Trip, Long> {
    List<Trip> findByPassengerId(long passengerId);
    List<Trip> findByDriverId(long driverId);
    List<Trip> findByStatus(TripStatus status);
    boolean lockAndAssignTrip(long tripId, long driverId, long vehicleId);
    boolean updateTripStatus(long tripId, TripStatus newStatus, int expectedVersion);
}

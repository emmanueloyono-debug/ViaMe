package com.viame.dao;

import com.viame.model.Driver;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Driver profile persistence: availability, live location, verification and earnings.
 */
public interface DriverDAO {
    Optional<Driver> findById(long driverId);

    /**
     * Approved, ONLINE drivers with an ACTIVE account and ACTIVE vehicle inside a
     * latitude/longitude bounding box (coarse pre-filter for the Haversine matcher).
     */
    List<Driver> findAvailableInBoundingBox(double minLat, double maxLat, double minLng, double maxLng);

    List<Driver> findByVerificationStatus(DriverVerificationStatus status);

    int countOnlineDrivers();

    boolean updateAvailability(long driverId, DriverAvailability availability);
    void updateAvailability(Connection conn, long driverId, DriverAvailability availability) throws SQLException;

    boolean updateLocation(long driverId, double lat, double lng);

    void creditEarnings(Connection conn, long driverId, BigDecimal amount) throws SQLException;

    boolean updateVerificationStatus(Connection conn, long driverId, DriverVerificationStatus status) throws SQLException;
}


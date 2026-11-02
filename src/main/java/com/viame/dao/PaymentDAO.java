package com.viame.dao;

import com.viame.model.Payment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface PaymentDAO {
    Payment insert(Connection conn, Payment payment) throws SQLException;
    Optional<Payment> findByRideId(long rideId);
    /** Loads the payment and locks its row ({@code SELECT ... FOR UPDATE}) until commit. */
    Optional<Payment> findByRideIdForUpdate(Connection conn, long rideId) throws SQLException;
    boolean update(Connection conn, Payment payment) throws SQLException;
    boolean deleteByRideId(Connection conn, long rideId) throws SQLException;
}


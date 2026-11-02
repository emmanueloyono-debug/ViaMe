package com.viame.dao;

import com.viame.model.Fare;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Immutable per-ride fare breakdown. */
public interface FareDAO {
    Fare insert(Connection conn, Fare fare) throws SQLException;
    Optional<Fare> findByRideId(long rideId);
}


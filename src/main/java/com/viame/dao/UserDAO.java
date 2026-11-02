package com.viame.dao;

import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.enums.UserStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Identity &amp; authentication persistence. Loaded users are returned polymorphically as
 * {@link Passenger}, {@link Driver} or {@link com.viame.model.Admin} depending on their role.
 *
 * <p>Methods taking a {@link Connection} participate in a caller-managed transaction.</p>
 */
public interface UserDAO extends GenericDAO<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /** Inserts the base {@code user} row and assigns the generated id to the entity. */
    long insertUser(Connection conn, User user) throws SQLException;
    void insertPassengerProfile(Connection conn, Passenger passenger) throws SQLException;
    void insertDriverProfile(Connection conn, Driver driver) throws SQLException;
    boolean updateStatus(Connection conn, long userId, UserStatus status) throws SQLException;
}


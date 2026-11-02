package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.TransactionManager;
import com.viame.dao.UserDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.enums.UserStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link UserDAO}. Every query is a parameter-bound
 * {@link PreparedStatement} (SQL-injection safe).
 */
public class UserDAOImpl implements UserDAO {

    private final TransactionManager transactionManager;

    public UserDAOImpl() {
        this(new TransactionManager());
    }

    public UserDAOImpl(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Optional<User> findById(Long id) {
        return findOne(UserRowMapper.BASE_SELECT + " WHERE u.id = ?", id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findOne(UserRowMapper.BASE_SELECT + " WHERE u.email = ?", email);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return findOne(UserRowMapper.BASE_SELECT + " WHERE u.phone = ?", phone);
    }

    @Override
    public List<User> findAll() {
        String sql = UserRowMapper.BASE_SELECT + " ORDER BY u.id";
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(UserRowMapper.map(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listing users", e);
        }
        return users;
    }

    @Override
    public boolean existsByEmail(String email) {
        return exists("SELECT 1 FROM `user` WHERE email = ?", email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return exists("SELECT 1 FROM `user` WHERE phone = ?", phone);
    }

    /**
     * Persists the user and its role-specific profile in one transaction.
     * Note: drivers are normally registered through {@code AuthenticationService.registerDriver}
     * which also creates the vehicle.
     */
    @Override
    public User save(User user) {
        return transactionManager.inTransaction(conn -> {
            insertUser(conn, user);
            if (user instanceof Passenger passenger) {
                insertPassengerProfile(conn, passenger);
            } else if (user instanceof Driver driver) {
                insertDriverProfile(conn, driver);
            }
            return user;
        });
    }

    @Override
    public long insertUser(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO `user` (email, phone, password_hash, password_salt, role, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getEmail());
            stmt.setString(2, user.getPhone());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getPasswordSalt());
            stmt.setString(5, user.getRole().name());
            stmt.setString(6, user.getStatus().name());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            return user.getId();
        }
    }

    @Override
    public void insertPassengerProfile(Connection conn, Passenger passenger) throws SQLException {
        String sql = "INSERT INTO passenger_profile (user_id, full_name, default_payment_method, wallet_balance) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, passenger.getId());
            stmt.setString(2, passenger.getFullName());
            stmt.setString(3, passenger.getDefaultPaymentMethod().name());
            stmt.setBigDecimal(4, passenger.getWalletBalance());
            stmt.executeUpdate();
        }
    }

    @Override
    public void insertDriverProfile(Connection conn, Driver driver) throws SQLException {
        String sql = """
            INSERT INTO driver_profile (user_id, full_name, licence_number, verification_status,
                                        rating_avg, availability, current_lat, current_lng, total_earnings)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, driver.getId());
            stmt.setString(2, driver.getFullName());
            stmt.setString(3, driver.getLicenceNumber());
            stmt.setString(4, driver.getVerificationStatus().name());
            stmt.setBigDecimal(5, driver.getRatingAvg());
            stmt.setString(6, driver.getAvailability().name());
            stmt.setDouble(7, driver.getCurrentLat());
            stmt.setDouble(8, driver.getCurrentLng());
            stmt.setBigDecimal(9, driver.getTotalEarnings());
            stmt.executeUpdate();
        }
    }

    /** Updates contact details, status and (for passengers/drivers) the display name. */
    @Override
    public boolean update(User user) {
        return transactionManager.inTransaction(conn -> {
            int rows;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE `user` SET email = ?, phone = ?, password_hash = ?, password_salt = ?, status = ? WHERE id = ?")) {
                stmt.setString(1, user.getEmail());
                stmt.setString(2, user.getPhone());
                stmt.setString(3, user.getPasswordHash());
                stmt.setString(4, user.getPasswordSalt());
                stmt.setString(5, user.getStatus().name());
                stmt.setLong(6, user.getId());
                rows = stmt.executeUpdate();
            }
            if (user instanceof Passenger passenger) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE passenger_profile SET full_name = ?, default_payment_method = ? WHERE user_id = ?")) {
                    stmt.setString(1, passenger.getFullName());
                    stmt.setString(2, passenger.getDefaultPaymentMethod().name());
                    stmt.setLong(3, passenger.getId());
                    stmt.executeUpdate();
                }
            } else if (user instanceof Driver driver) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE driver_profile SET full_name = ? WHERE user_id = ?")) {
                    stmt.setString(1, driver.getFullName());
                    stmt.setLong(2, driver.getId());
                    stmt.executeUpdate();
                }
            }
            return rows > 0;
        });
    }

    @Override
    public boolean updateStatus(Connection conn, long userId, UserStatus status) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE `user` SET status = ? WHERE id = ?")) {
            stmt.setString(1, status.name());
            stmt.setLong(2, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** Hard delete (profiles cascade). Fails if the user is referenced by rides. */
    @Override
    public boolean delete(Long id) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM `user` WHERE id = ?")) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting user: " + id, e);
        }
    }

    private Optional<User> findOne(String sql, Object param) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(UserRowMapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving user", e);
        }
    }

    private boolean exists(String sql, String param) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error checking user existence", e);
        }
    }
}


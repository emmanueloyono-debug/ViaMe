package com.viame.dao.impl;

import com.viame.model.Admin;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Maps a joined {@code user + passenger_profile + driver_profile} row to the correct
 * {@link User} subtype (runtime polymorphism driven by the {@code role} column).
 */
final class UserRowMapper {

    /** Base SELECT shared by user/passenger/driver DAOs. Append a WHERE clause. */
    static final String BASE_SELECT = """
        SELECT u.id, u.email, u.phone, u.password_hash, u.password_salt, u.role, u.status, u.created_at,
               p.full_name AS p_full_name, p.default_payment_method, p.wallet_balance,
               d.full_name AS d_full_name, d.licence_number, d.verification_status, d.rating_avg,
               d.availability, d.current_lat, d.current_lng, d.total_earnings
        FROM `user` u
        LEFT JOIN passenger_profile p ON p.user_id = u.id
        LEFT JOIN driver_profile d ON d.user_id = u.id
        """;

    private UserRowMapper() {
    }

    static User map(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        String hash = rs.getString("password_hash");
        String salt = rs.getString("password_salt");
        UserStatus status = UserStatus.valueOf(rs.getString("status"));
        UserRole role = UserRole.valueOf(rs.getString("role"));

        User user = switch (role) {
            case PASSENGER -> new Passenger(id, email, phone, hash, salt, status,
                    rs.getString("p_full_name"),
                    parseEnum(PaymentMethod.class, rs.getString("default_payment_method")),
                    rs.getBigDecimal("wallet_balance"));
            case DRIVER -> new Driver(id, email, phone, hash, salt, status,
                    rs.getString("d_full_name"),
                    rs.getString("licence_number"),
                    parseEnum(DriverVerificationStatus.class, rs.getString("verification_status")),
                    rs.getBigDecimal("rating_avg"),
                    parseEnum(DriverAvailability.class, rs.getString("availability")),
                    rs.getDouble("current_lat"),
                    rs.getDouble("current_lng"),
                    rs.getBigDecimal("total_earnings"));
            case ADMIN -> new Admin(id, email, phone, hash, salt, status);
        };

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            user.setCreatedAt(created.toLocalDateTime());
        }
        return user;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null; // Unknown legacy value -> entity constructor applies its default
        }
    }
}


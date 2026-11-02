package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.RatingDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.Rating;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class RatingDAOImpl implements RatingDAO {

    @Override
    public Rating save(Rating rating) {
        String sql = "INSERT INTO rating (ride_id, rater_id, ratee_id, score, comment, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, rating.getRideId());
            stmt.setLong(2, rating.getRaterId());
            stmt.setLong(3, rating.getRateeId());
            stmt.setInt(4, rating.getScore());
            if (rating.getComment() != null) stmt.setString(5, rating.getComment()); else stmt.setNull(5, Types.VARCHAR);
            stmt.setTimestamp(6, Timestamp.valueOf(rating.getCreatedAt()));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    rating.setId(keys.getLong(1));
                }
            }
            return rating;
        } catch (SQLException e) {
            throw new DatabaseException("Error saving rating for ride: " + rating.getRideId(), e);
        }
    }

    @Override
    public boolean existsByRideAndRater(long rideId, long raterId) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM rating WHERE ride_id = ? AND rater_id = ?")) {
            stmt.setLong(1, rideId);
            stmt.setLong(2, raterId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error checking existing rating", e);
        }
    }

    @Override
    public List<Rating> findByRatee(long rateeId) {
        List<Rating> ratings = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM rating WHERE ratee_id = ? ORDER BY created_at DESC")) {
            stmt.setLong(1, rateeId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp created = rs.getTimestamp("created_at");
                    ratings.add(new Rating(
                            rs.getLong("id"),
                            rs.getLong("ride_id"),
                            rs.getLong("rater_id"),
                            rs.getLong("ratee_id"),
                            rs.getInt("score"),
                            rs.getString("comment"),
                            created != null ? created.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listing ratings for user: " + rateeId, e);
        }
        return ratings;
    }
}


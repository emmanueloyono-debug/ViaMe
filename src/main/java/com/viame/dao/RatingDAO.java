package com.viame.dao;

import com.viame.model.Rating;

import java.util.List;

/**
 * Two-way rating persistence. The {@code trg_after_rating_insert} trigger recalculates
 * {@code driver_profile.rating_avg} automatically.
 */
public interface RatingDAO {
    Rating save(Rating rating);
    boolean existsByRideAndRater(long rideId, long raterId);
    List<Rating> findByRatee(long rateeId);
}


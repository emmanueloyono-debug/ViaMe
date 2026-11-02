package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.RatingDAO;
import com.viame.dao.TripDAO;
import com.viame.dao.impl.AuditLogDAOImpl;
import com.viame.dao.impl.RatingDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.exception.ValidationException;
import com.viame.model.Rating;
import com.viame.model.Trip;
import com.viame.model.enums.TripStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Two-way Rating and Feedback Service (UC08).
 * Manages post-ride evaluations with automatic aggregate score updates.
 */
public class RatingService {

    private final RatingDAO ratingDAO;
    private final TripDAO tripDAO;
    private final AuditLogDAO auditLogDAO;

    public RatingService() {
        this(new RatingDAOImpl(), new TripDAOImpl(), new AuditLogDAOImpl());
    }

    public RatingService(RatingDAO ratingDAO, TripDAO tripDAO, AuditLogDAO auditLogDAO) {
        this.ratingDAO = ratingDAO;
        this.tripDAO = tripDAO;
        this.auditLogDAO = auditLogDAO;
    }

    /**
     * Submits a rating for a completed trip.
     */
    public Rating submitRating(long tripId, long raterId, long rateeId, int score, String comment) {
        if (score < 1 || score > 5) {
            throw new ValidationException("Rating score must be between 1 and 5 stars");
        }

        Trip trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new ValidationException("Ratings can only be submitted for completed trips");
        }

        if (ratingDAO.existsByRideAndRater(tripId, raterId)) {
            throw new ValidationException("You have already submitted a rating for this trip");
        }

        Rating rating = new Rating(0, tripId, raterId, rateeId, score, comment, LocalDateTime.now());
        Rating saved = ratingDAO.save(rating);
        auditLogDAO.log(raterId, "RATING_SUBMITTED", "rating", saved.getId());
        return saved;
    }

    public List<Rating> getRatingsForUser(long userId) {
        return ratingDAO.findByRatee(userId);
    }
}


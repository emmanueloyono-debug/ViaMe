package com.viame.model;

import java.time.LocalDateTime;

public class Rating {
    private long id;
    private long rideId;
    private long raterId;
    private long rateeId;
    private int score;
    private String comment;
    private LocalDateTime createdAt;

    public Rating(long id, long rideId, long raterId, long rateeId, int score, String comment, LocalDateTime createdAt) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Rating score must be between 1 and 5");
        }
        this.id = id;
        this.rideId = rideId;
        this.raterId = raterId;
        this.rateeId = rateeId;
        this.score = score;
        this.comment = comment;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRideId() { return rideId; }
    public void setRideId(long rideId) { this.rideId = rideId; }

    public long getRaterId() { return raterId; }
    public void setRaterId(long raterId) { this.raterId = raterId; }

    public long getRateeId() { return rateeId; }
    public void setRateeId(long rateeId) { this.rateeId = rateeId; }

    public int getScore() { return score; }
    public void setScore(int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Rating score must be between 1 and 5");
        }
        this.score = score;
    }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

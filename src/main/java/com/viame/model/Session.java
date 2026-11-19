package com.viame.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an active authenticated REST API session.
 * Maps to the {@code session} table in MySQL {@code viame_db}.
 */
public class Session {

    private String tokenHash;
    private long userId;
    private Instant expiresAt;
    private String ipAddress;
    private Instant createdAt;

    public Session() {
    }

    public Session(String tokenHash, long userId, Instant expiresAt, String ipAddress) {
        this(tokenHash, userId, expiresAt, ipAddress, Instant.now());
    }

    public Session(String tokenHash, long userId, Instant expiresAt, String ipAddress, Instant createdAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Session session = (Session) o;
        return userId == session.userId && Objects.equals(tokenHash, session.tokenHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenHash, userId);
    }

    @Override
    public String toString() {
        return "Session{" +
                "tokenHash='" + tokenHash + '\'' +
                ", userId=" + userId +
                ", expiresAt=" + expiresAt +
                ", ipAddress='" + ipAddress + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

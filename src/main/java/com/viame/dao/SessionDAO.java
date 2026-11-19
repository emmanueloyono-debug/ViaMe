package com.viame.dao;

import com.viame.model.Session;

import java.util.Optional;

/**
 * Data Access Object for REST session tokens stored in the {@code session} table.
 */
public interface SessionDAO extends GenericDAO<Session, String> {

    /**
     * Finds a session by its unique SHA-256 token hash.
     */
    Optional<Session> findByTokenHash(String tokenHash);

    /**
     * Deletes a session by its token hash (e.g. during logout).
     */
    boolean deleteByTokenHash(String tokenHash);

    /**
     * Deletes all sessions associated with a specific user id.
     */
    boolean deleteByUserId(long userId);

    /**
     * Purges all expired sessions from the database.
     *
     * @return number of purged session rows
     */
    int deleteExpired();
}

package com.viame.dao;

import com.viame.exception.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executes a unit of work inside a single ACID JDBC transaction.
 *
 * <p>Opens one connection, disables auto-commit, runs the work, and commits. Any
 * {@link SQLException} or {@link RuntimeException} (including business exceptions such as
 * {@code InsufficientFundsException}) triggers a full rollback. Business exceptions are
 * re-thrown unchanged; SQL errors are wrapped in {@link DatabaseException}.</p>
 *
 * <p>Services receive an instance through their constructor so that unit tests can
 * substitute a fake implementation.</p>
 */
public class TransactionManager {

    @FunctionalInterface
    public interface TransactionalWork<T> {
        T execute(Connection conn) throws SQLException;
    }

    public <T> T inTransaction(TransactionalWork<T> work) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new DatabaseException("Transaction failed and was rolled back", e);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database connection error during transaction", e);
        }
    }
}


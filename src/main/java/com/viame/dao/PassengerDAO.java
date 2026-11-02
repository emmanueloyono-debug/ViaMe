package com.viame.dao;

import com.viame.model.Passenger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Passenger profile and digital wallet persistence.
 */
public interface PassengerDAO {
    Optional<Passenger> findById(long passengerId);

    BigDecimal getWalletBalance(Connection conn, long passengerId) throws SQLException;

    void creditWallet(Connection conn, long passengerId, BigDecimal amount) throws SQLException;

    /**
     * Atomically debits the wallet only if the balance is sufficient
     * ({@code UPDATE ... WHERE wallet_balance >= amount}).
     *
     * @return {@code false} if funds were insufficient (nothing was changed)
     */
    boolean debitWallet(Connection conn, long passengerId, BigDecimal amount) throws SQLException;
}


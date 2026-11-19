package com.viame.dao;

import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.model.Passenger;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PassengerDAO Database Integration Tests")
class PassengerDAOTest {

    private PassengerDAO passengerDAO;
    private UserDAO userDAO;
    private Passenger testPassenger;

    @BeforeEach
    void setUp() {
        passengerDAO = new PassengerDAOImpl();
        userDAO = new UserDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "pass.dao." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "DAO Passenger " + tag,
                PaymentMethod.WALLET, new BigDecimal("5000.00"));
        userDAO.save(testPassenger);
    }

    @AfterEach
    void tearDown() {
        if (testPassenger != null && testPassenger.getId() > 0) {
            try {
                userDAO.delete(testPassenger.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Find passenger by ID for seed and created passengers")
    void testFindById() {
        // Seed passenger Alice (ID 4)
        Optional<Passenger> aliceOpt = passengerDAO.findById(4L);
        assertTrue(aliceOpt.isPresent());
        assertEquals("Alice Mengue", aliceOpt.get().getFullName());

        // Dynamic test passenger
        Optional<Passenger> testOpt = passengerDAO.findById(testPassenger.getId());
        assertTrue(testOpt.isPresent());
        assertEquals(testPassenger.getFullName(), testOpt.get().getFullName());
        assertEquals(0, new BigDecimal("5000.00").compareTo(testOpt.get().getWalletBalance()));

        // Non-existent passenger
        Optional<Passenger> nonExistent = passengerDAO.findById(9999999L);
        assertTrue(nonExistent.isEmpty());

        // User who is not a passenger (ID 1 is admin, ID 2 is driver)
        Optional<Passenger> notPassenger = passengerDAO.findById(1L);
        assertTrue(notPassenger.isEmpty());
    }

    @Test
    @DisplayName("Get wallet balance, credit wallet and debit wallet atomically")
    void testWalletOperations() throws Exception {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            // Check initial balance
            BigDecimal initial = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("5000.00").compareTo(initial));

            // Credit wallet +2500
            passengerDAO.creditWallet(conn, testPassenger.getId(), new BigDecimal("2500.00"));
            BigDecimal afterCredit = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("7500.00").compareTo(afterCredit));

            // Debit wallet -3000 (successful)
            boolean debited = passengerDAO.debitWallet(conn, testPassenger.getId(), new BigDecimal("3000.00"));
            assertTrue(debited, "Debit should succeed when balance is sufficient");
            BigDecimal afterDebit = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("4500.00").compareTo(afterDebit));

            // Debit wallet -10000 (insufficient balance -> should return false and not debit)
            boolean failedDebit = passengerDAO.debitWallet(conn, testPassenger.getId(), new BigDecimal("10000.00"));
            assertFalse(failedDebit, "Debit should fail when balance is insufficient");
            BigDecimal unchanged = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("4500.00").compareTo(unchanged));
        }
    }

    @Test
    @DisplayName("Wallet operations on non-existent passenger throw EntityNotFoundException")
    void testWalletOperationsNonExistent() throws Exception {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            assertThrows(EntityNotFoundException.class, () ->
                    passengerDAO.getWalletBalance(conn, 9999999L));
            assertThrows(EntityNotFoundException.class, () ->
                    passengerDAO.creditWallet(conn, 9999999L, new BigDecimal("500.00")));
            assertFalse(passengerDAO.debitWallet(conn, 9999999L, new BigDecimal("500.00")));
        }
    }
}

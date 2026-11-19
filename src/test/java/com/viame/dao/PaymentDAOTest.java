package com.viame.dao;

import com.viame.dao.impl.PaymentDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.model.Passenger;
import com.viame.model.Payment;
import com.viame.model.Trip;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.PaymentStatus;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentDAO Database Integration Tests")
class PaymentDAOTest {

    private PaymentDAO paymentDAO;
    private TripDAO tripDAO;
    private UserDAO userDAO;

    private Passenger testPassenger;
    private Trip testTrip;

    @BeforeEach
    void setUp() {
        paymentDAO = new PaymentDAOImpl();
        tripDAO = new TripDAOImpl();
        userDAO = new UserDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "pay.pass." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Payment Passenger " + tag,
                PaymentMethod.WALLET, new BigDecimal("10000.00"));
        userDAO.save(testPassenger);

        testTrip = new Trip(0, testPassenger.getId(), null, null, 1L,
                4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("6.00"), 18, TripStatus.REQUESTED, 1);
        tripDAO.save(testTrip);
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            if (testTrip != null && testTrip.getId() > 0) {
                paymentDAO.deleteByRideId(conn, testTrip.getId());
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ride WHERE id = ?")) {
                    stmt.setLong(1, testTrip.getId());
                    stmt.executeUpdate();
                }
            }
            if (testPassenger != null && testPassenger.getId() > 0) {
                userDAO.delete(testPassenger.getId());
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Find seed payment by ride ID")
    void testFindSeedPayment() {
        Optional<Payment> seedPayment = paymentDAO.findByRideId(1L);
        assertTrue(seedPayment.isPresent(), "Payment for seed ride 1 must exist");
        assertEquals(1L, seedPayment.get().getRideId());
        assertEquals(0, new BigDecimal("2690.00").compareTo(seedPayment.get().getAmount()));
        assertEquals(PaymentMethod.WALLET, seedPayment.get().getMethod());
        assertEquals(PaymentStatus.PAID, seedPayment.get().getStatus());
        assertEquals("TXN-20261025-001", seedPayment.get().getTransactionRef());
    }

    @Test
    @DisplayName("Insert, find for update, update and delete payment lifecycle")
    void testPaymentLifecycle() throws Exception {
        Payment payment = new Payment(0, testTrip.getId(), new BigDecimal("3200.00"),
                PaymentMethod.WALLET, PaymentStatus.PENDING, "TXN-TEST-100", LocalDateTime.now());

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            Payment inserted = paymentDAO.insert(conn, payment);
            assertNotNull(inserted);
            assertTrue(inserted.getId() > 0);

            // Find for update
            Optional<Payment> forUpdate = paymentDAO.findByRideIdForUpdate(conn, testTrip.getId());
            assertTrue(forUpdate.isPresent());
            assertEquals(PaymentStatus.PENDING, forUpdate.get().getStatus());

            // Update to PAID
            forUpdate.get().setStatus(PaymentStatus.PAID);
            forUpdate.get().setTransactionRef("TXN-TEST-PAID");
            boolean updated = paymentDAO.update(conn, forUpdate.get());
            assertTrue(updated);
        }

        // Verify update persisted
        Optional<Payment> retrieved = paymentDAO.findByRideId(testTrip.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(PaymentStatus.PAID, retrieved.get().getStatus());
        assertEquals("TXN-TEST-PAID", retrieved.get().getTransactionRef());

        // Delete payment by ride ID
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            boolean deleted = paymentDAO.deleteByRideId(conn, testTrip.getId());
            assertTrue(deleted);
        }

        assertTrue(paymentDAO.findByRideId(testTrip.getId()).isEmpty());
    }
}

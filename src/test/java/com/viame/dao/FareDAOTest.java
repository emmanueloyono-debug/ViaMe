package com.viame.dao;

import com.viame.dao.impl.FareDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.model.Fare;
import com.viame.model.Passenger;
import com.viame.model.Trip;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FareDAO Database Integration Tests")
class FareDAOTest {

    private FareDAO fareDAO;
    private TripDAO tripDAO;
    private UserDAO userDAO;

    private Passenger testPassenger;
    private Trip testTrip;

    @BeforeEach
    void setUp() {
        fareDAO = new FareDAOImpl();
        tripDAO = new TripDAOImpl();
        userDAO = new UserDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "fare.pass." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Fare Passenger " + tag,
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
    @DisplayName("Find seed fare by ride ID")
    void testFindSeedFare() {
        Optional<Fare> seedFare = fareDAO.findByRideId(1L);
        assertTrue(seedFare.isPresent(), "Fare for seed ride 1 must exist");
        assertEquals(1L, seedFare.get().getRideId());
        assertEquals(0, new BigDecimal("500.00").compareTo(seedFare.get().getBaseFare()));
        assertEquals(0, new BigDecimal("1640.00").compareTo(seedFare.get().getDistanceComponent()));
        assertEquals(0, new BigDecimal("550.00").compareTo(seedFare.get().getTimeComponent()));
        assertEquals(0, new BigDecimal("2690.00").compareTo(seedFare.get().getTotalAmount()));
        assertEquals("XAF", seedFare.get().getCurrency());
    }

    @Test
    @DisplayName("Insert and retrieve fare for a new trip")
    void testInsertAndRetrieveFare() throws Exception {
        Fare fare = new Fare(0, testTrip.getId(), new BigDecimal("500.00"),
                new BigDecimal("1200.00"), new BigDecimal("450.00"),
                new BigDecimal("1.20"), new BigDecimal("2580.00"), "XAF");

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            Fare inserted = fareDAO.insert(conn, fare);
            assertNotNull(inserted);
            assertTrue(inserted.getId() > 0);
        }

        Optional<Fare> retrieved = fareDAO.findByRideId(testTrip.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(testTrip.getId(), retrieved.get().getRideId());
        assertEquals(0, new BigDecimal("2580.00").compareTo(retrieved.get().getTotalAmount()));
        assertEquals(0, new BigDecimal("1.20").compareTo(retrieved.get().getSurgeMultiplier()));
    }
}

package com.viame.dao;

import com.viame.dao.impl.RatingDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Rating;
import com.viame.model.Trip;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RatingDAO Database Integration Tests")
class RatingDAOTest {

    private RatingDAO ratingDAO;
    private TripDAO tripDAO;
    private UserDAO userDAO;

    private Passenger testPassenger;
    private Driver testDriver;
    private Trip testTrip;

    @BeforeEach
    void setUp() {
        ratingDAO = new RatingDAOImpl();
        tripDAO = new TripDAOImpl();
        userDAO = new UserDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "rate.pass." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Rating Passenger " + tag,
                PaymentMethod.WALLET, new BigDecimal("5000.00"));
        userDAO.save(testPassenger);

        testDriver = new Driver(0, "rate.drv." + tag + "@viame.test",
                "+2376" + String.format("%08d", (Long.parseLong(tag) + 1) % 100000000),
                "hash", "salt", UserStatus.ACTIVE, "Rating Driver " + tag,
                "LIC-RATE-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(testDriver);

        testTrip = new Trip(0, testPassenger.getId(), testDriver.getId(), 1L, 1L,
                4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("5.00"), 15, TripStatus.COMPLETED, 4);
        tripDAO.save(testTrip);
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            if (testTrip != null && testTrip.getId() > 0) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM rating WHERE ride_id = ?")) {
                    stmt.setLong(1, testTrip.getId());
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ride WHERE id = ?")) {
                    stmt.setLong(1, testTrip.getId());
                    stmt.executeUpdate();
                }
            }
            if (testDriver != null && testDriver.getId() > 0) {
                userDAO.delete(testDriver.getId());
            }
            if (testPassenger != null && testPassenger.getId() > 0) {
                userDAO.delete(testPassenger.getId());
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Find seed ratings and verify existsByRideAndRater")
    void testSeedRatings() {
        // Ride 1 rater 4 (Alice) rated driver 2 (Samuel) with score 5
        assertTrue(ratingDAO.existsByRideAndRater(1L, 4L));
        assertFalse(ratingDAO.existsByRideAndRater(1L, 999999L));

        // Find ratings for driver 2 (Samuel)
        List<Rating> samuelRatings = ratingDAO.findByRatee(2L);
        assertNotNull(samuelRatings);
        assertFalse(samuelRatings.isEmpty(), "Samuel should have seed ratings");
        assertTrue(samuelRatings.stream().anyMatch(r -> r.getScore() == 5));
    }

    @Test
    @DisplayName("Save rating and verify retrieval by ratee")
    void testSaveAndFindByRatee() {
        Rating rating = new Rating(0, testTrip.getId(), testPassenger.getId(), testDriver.getId(),
                5, "Superb ride, very clean vehicle!", LocalDateTime.now());

        Rating saved = ratingDAO.save(rating);
        assertNotNull(saved);
        assertTrue(saved.getId() > 0);

        assertTrue(ratingDAO.existsByRideAndRater(testTrip.getId(), testPassenger.getId()));

        List<Rating> ratings = ratingDAO.findByRatee(testDriver.getId());
        assertEquals(1, ratings.size());
        Rating retrieved = ratings.get(0);
        assertEquals(5, retrieved.getScore());
        assertEquals("Superb ride, very clean vehicle!", retrieved.getComment());
        assertEquals(testTrip.getId(), retrieved.getRideId());
    }
}

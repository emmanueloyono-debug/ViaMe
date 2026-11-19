package com.viame.service;

import com.viame.dao.DBConnection;
import com.viame.dao.TripDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Rating;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.UserStatus;
import com.viame.model.enums.VehicleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RatingService Integration Tests")
class RatingServiceTest {

    private RatingService ratingService;
    private TripDAO tripDAO;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;

    private Passenger testPassenger;
    private Driver testDriver;
    private Vehicle testVehicle;
    private Trip testTrip;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService();
        tripDAO = new TripDAOImpl();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "rate.svc.pass." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Rating Svc Passenger",
                PaymentMethod.WALLET, new BigDecimal("5000.00"));
        userDAO.save(testPassenger);

        testDriver = new Driver(0, "rate.svc.drv." + tag + "@viame.test",
                "+2376" + String.format("%08d", (Long.parseLong(tag) + 1) % 100000000),
                "hash", "salt", UserStatus.ACTIVE, "Rating Svc Driver",
                "LIC-RATESVC-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(testDriver);

        testVehicle = new Vehicle(0, testDriver.getId(), "LT " + tag.substring(0, 3) + " RT",
                "Toyota", "Yaris", 4, VehicleStatus.ACTIVE);
        vehicleDAO.save(testVehicle);

        testTrip = new Trip(0, testPassenger.getId(), testDriver.getId(), testVehicle.getId(), 1L,
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
                } catch (Exception ignored) {
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ride WHERE id = ?")) {
                    stmt.setLong(1, testTrip.getId());
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
            if (testVehicle != null && testVehicle.getId() > 0) {
                vehicleDAO.delete(testVehicle.getId());
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
    @DisplayName("Submit rating validates star score range 1..5")
    void testScoreValidation() {
        assertThrows(ValidationException.class, () ->
                ratingService.submitRating(testTrip.getId(), testPassenger.getId(), testDriver.getId(), 0, "Bad"));
        assertThrows(ValidationException.class, () ->
                ratingService.submitRating(testTrip.getId(), testPassenger.getId(), testDriver.getId(), 6, "Too high"));
    }

    @Test
    @DisplayName("Submit rating succeeds for completed trip and rejects duplicates")
    void testSubmitRatingAndDuplicateRejection() {
        Rating rating = ratingService.submitRating(testTrip.getId(), testPassenger.getId(),
                testDriver.getId(), 5, "Great driver!");
        assertNotNull(rating);
        assertTrue(rating.getId() > 0);
        assertEquals(5, rating.getScore());

        // Duplicate submission on same trip by same rater
        assertThrows(ValidationException.class, () ->
                ratingService.submitRating(testTrip.getId(), testPassenger.getId(),
                        testDriver.getId(), 4, "Second rating"));

        // Query ratings for user
        List<Rating> ratings = ratingService.getRatingsForUser(testDriver.getId());
        assertEquals(1, ratings.size());
        assertEquals("Great driver!", ratings.get(0).getComment());
    }

    @Test
    @DisplayName("Cannot submit rating for uncompleted trip")
    void testCannotRateUncompletedTrip() {
        testTrip.setStatus(TripStatus.IN_PROGRESS);
        tripDAO.update(testTrip);

        assertThrows(ValidationException.class, () ->
                ratingService.submitRating(testTrip.getId(), testPassenger.getId(),
                        testDriver.getId(), 5, "premature rating"));
    }
}

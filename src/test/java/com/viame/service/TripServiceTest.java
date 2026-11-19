package com.viame.service;

import com.viame.dao.DBConnection;
import com.viame.dao.DriverDAO;
import com.viame.dao.TripDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.IllegalStateTransitionException;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TripService Lifecycle and Workflow Integration Tests")
class TripServiceTest {

    private TripService tripService;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;
    private DriverDAO driverDAO;
    private TripDAO tripDAO;

    private Passenger testPassenger;
    private Driver testDriver;
    private Vehicle testVehicle;
    private final List<Long> createdTripIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tripService = new TripService();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();
        driverDAO = new DriverDAOImpl();
        tripDAO = new TripDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "trip.svc.pass." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Trip Svc Passenger " + tag,
                PaymentMethod.WALLET, new BigDecimal("50000.00"));
        userDAO.save(testPassenger);

        testDriver = new Driver(0, "trip.svc.drv." + tag + "@viame.test",
                "+2376" + String.format("%08d", (Long.parseLong(tag) + 1) % 100000000),
                "hash", "salt", UserStatus.ACTIVE, "Trip Svc Driver " + tag,
                "LIC-TRIPSVC-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(testDriver);

        testVehicle = new Vehicle(0, testDriver.getId(), "LT " + tag.substring(0, 3) + " TS",
                "Toyota", "Yaris", 4, VehicleStatus.ACTIVE);
        vehicleDAO.save(testVehicle);
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            for (Long tripId : createdTripIds) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payment WHERE ride_id = ?")) {
                    stmt.setLong(1, tripId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM fare WHERE ride_id = ?")) {
                    stmt.setLong(1, tripId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ride WHERE id = ?")) {
                    stmt.setLong(1, tripId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        createdTripIds.clear();

        if (testVehicle != null && testVehicle.getId() > 0) {
            try {
                vehicleDAO.delete(testVehicle.getId());
            } catch (Exception ignored) {
            }
        }
        if (testDriver != null && testDriver.getId() > 0) {
            try {
                userDAO.delete(testDriver.getId());
            } catch (Exception ignored) {
            }
        }
        if (testPassenger != null && testPassenger.getId() > 0) {
            try {
                userDAO.delete(testPassenger.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Request trip validates parameters and enforces active trip rule")
    void testRequestTrip() {
        // Blank address validation
        assertThrows(ValidationException.class, () ->
                tripService.requestTrip(testPassenger.getId(), 4.05, 9.76, "", 4.07, 9.71, "Bonamoussadi", PaymentMethod.CASH));

        // Successful trip request
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi", PaymentMethod.CASH);
        assertNotNull(trip);
        assertTrue(trip.getId() > 0);
        assertEquals(TripStatus.REQUESTED, trip.getStatus());
        createdTripIds.add(trip.getId());

        // Attempting to request another trip while one is active throws ValidationException
        assertThrows(ValidationException.class, () ->
                tripService.requestTrip(testPassenger.getId(), 4.05, 9.76, "Akwa", 4.07, 9.71, "Airport", PaymentMethod.CASH));
    }

    @Test
    @DisplayName("Request trip with insufficient wallet funds is rejected")
    void testRequestTripInsufficientFunds() {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        Passenger brokePassenger = new Passenger(0, "broke." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Broke Passenger",
                PaymentMethod.WALLET, new BigDecimal("100.00")); // only 100 XAF
        userDAO.save(brokePassenger);

        try {
            assertThrows(InsufficientFundsException.class, () ->
                    tripService.requestTrip(brokePassenger.getId(), 4.051056, 9.767869, "Akwa",
                            4.075833, 9.718611, "Bonamoussadi", PaymentMethod.WALLET));
        } finally {
            userDAO.delete(brokePassenger.getId());
        }
    }

    @Test
    @DisplayName("Full ride lifecycle: REQUESTED -> ASSIGNED -> DRIVER_EN_ROUTE -> ARRIVED -> IN_PROGRESS -> COMPLETED")
    void testCompleteRideLifecycle() {
        // 1. Request
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi", PaymentMethod.CASH);
        createdTripIds.add(trip.getId());

        // 2. Accept
        Trip accepted = tripService.acceptTrip(trip.getId(), testDriver.getId());
        assertEquals(TripStatus.ASSIGNED, accepted.getStatus());
        assertEquals(testDriver.getId(), accepted.getDriverId());

        // 3. Driver en route
        Trip enRoute = tripService.driverEnRoute(trip.getId(), testDriver.getId());
        assertEquals(TripStatus.DRIVER_EN_ROUTE, enRoute.getStatus());

        // 4. Arrived
        Trip arrived = tripService.driverArrived(trip.getId(), testDriver.getId());
        assertEquals(TripStatus.ARRIVED, arrived.getStatus());

        // 5. In progress
        Trip inProgress = tripService.startTrip(trip.getId(), testDriver.getId());
        assertEquals(TripStatus.IN_PROGRESS, inProgress.getStatus());

        // 6. Complete
        Trip completed = tripService.completeTrip(trip.getId(), testDriver.getId(), PaymentMethod.CASH);
        assertEquals(TripStatus.COMPLETED, completed.getStatus());

        // Verify driver is back online
        Driver updatedDriver = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(DriverAvailability.ONLINE, updatedDriver.getAvailability());
    }

    @Test
    @DisplayName("Wrong driver cannot transition or complete trip")
    void testWrongDriverTransitions() {
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi", PaymentMethod.CASH);
        createdTripIds.add(trip.getId());
        tripService.acceptTrip(trip.getId(), testDriver.getId());

        long impostorDriverId = 3L; // Eric
        assertThrows(ValidationException.class, () ->
                tripService.driverEnRoute(trip.getId(), impostorDriverId));
        assertThrows(ValidationException.class, () ->
                tripService.completeTrip(trip.getId(), impostorDriverId, PaymentMethod.CASH));
    }

    @Test
    @DisplayName("Cancel trip transitions to CANCELLED and resets driver availability")
    void testCancelTrip() {
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi", PaymentMethod.CASH);
        createdTripIds.add(trip.getId());
        tripService.acceptTrip(trip.getId(), testDriver.getId());

        Trip cancelled = tripService.cancelTrip(trip.getId(), testPassenger.getId(), true);
        assertEquals(TripStatus.CANCELLED, cancelled.getStatus());

        Driver driver = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(DriverAvailability.ONLINE, driver.getAvailability());
    }

    @Test
    @DisplayName("Query helpers return accurate passenger and driver trip lists")
    void testQueryHelpers() {
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa",
                4.075833, 9.718611, "Airport", PaymentMethod.CASH);
        createdTripIds.add(trip.getId());

        List<Trip> passengerTrips = tripService.getTripsForPassenger(testPassenger.getId());
        assertEquals(1, passengerTrips.size());

        List<Trip> pending = tripService.getPendingBroadcastRequests();
        assertTrue(pending.stream().anyMatch(t -> t.getId() == trip.getId()));

        tripService.acceptTrip(trip.getId(), testDriver.getId());
        List<Trip> driverTrips = tripService.getTripsForDriver(testDriver.getId());
        assertEquals(1, driverTrips.size());
    }
}

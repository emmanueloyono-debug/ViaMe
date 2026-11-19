package com.viame.dao;

import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TripDAO Database Integration Tests")
class TripDAOTest {

    private TripDAO tripDAO;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;
    private DriverDAO driverDAO;

    private Passenger testPassenger;
    private Driver testDriver;
    private Vehicle testVehicle;
    private final List<Long> createdTripIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tripDAO = new TripDAOImpl();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();
        driverDAO = new DriverDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "pass.trip." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Trip Test Passenger " + tag,
                PaymentMethod.WALLET, new BigDecimal("15000.00"));
        userDAO.save(testPassenger);

        testDriver = new Driver(0, "drv.trip." + tag + "@viame.test",
                "+2376" + String.format("%08d", (Long.parseLong(tag) + 1) % 100000000),
                "hash", "salt", UserStatus.ACTIVE, "Trip Test Driver " + tag,
                "LIC-TRIP-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(testDriver);

        testVehicle = new Vehicle(0, testDriver.getId(), "LT " + tag.substring(0, 3) + " TP",
                "Toyota", "Yaris", 4, VehicleStatus.ACTIVE);
        vehicleDAO.save(testVehicle);
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            for (Long tripId : createdTripIds) {
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
    @DisplayName("Find seed trips by ID and list all trips")
    void testFindSeedTrips() {
        Optional<Trip> ride1 = tripDAO.findById(1L);
        assertTrue(ride1.isPresent(), "Seed ride 1 must exist");
        assertEquals(4L, ride1.get().getPassengerId());
        assertEquals(2L, ride1.get().getDriverId());
        assertEquals(TripStatus.COMPLETED, ride1.get().getStatus());
        assertEquals("Akwa Palace Hotel, Douala", ride1.get().getPickupAddress());

        List<Trip> all = tripDAO.findAll();
        assertNotNull(all);
        assertTrue(all.size() >= 5, "Database must have at least 5 seed rides");
    }

    @Test
    @DisplayName("Save, update status, and find active trip by passenger")
    void testTripLifecycleAndActiveTrip() {
        Trip trip = new Trip(0, testPassenger.getId(), null, null, 1L,
                4.051056, 9.767869, "Akwa Center",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("5.50"), 15, TripStatus.REQUESTED, 1);

        Trip saved = tripDAO.save(trip);
        assertNotNull(saved);
        assertTrue(saved.getId() > 0);
        createdTripIds.add(saved.getId());

        // Find active by passenger
        Optional<Trip> active = tripDAO.findActiveByPassengerId(testPassenger.getId());
        assertTrue(active.isPresent());
        assertEquals(saved.getId(), active.get().getId());
        assertEquals(TripStatus.REQUESTED, active.get().getStatus());

        // Query by status
        List<Trip> requestedTrips = tripDAO.findByStatus(TripStatus.REQUESTED);
        assertTrue(requestedTrips.stream().anyMatch(t -> t.getId() == saved.getId()));

        // Update trip status optimistically (version 1 -> 2)
        boolean statusUpdated = tripDAO.updateTripStatus(saved.getId(), TripStatus.ARRIVED, 1);
        assertTrue(statusUpdated);

        Trip reFetched = tripDAO.findById(saved.getId()).orElseThrow();
        assertEquals(TripStatus.ARRIVED, reFetched.getStatus());
        assertEquals(2, reFetched.getVersion());

        // Optimistic conflict with stale version (trying to update with version 1 again)
        boolean staleUpdate = tripDAO.updateTripStatus(saved.getId(), TripStatus.IN_PROGRESS, 1);
        assertFalse(staleUpdate, "Stale version update should fail");
    }

    @Test
    @DisplayName("Pessimistic lockAndAssignTrip assigns driver and changes driver availability to ON_TRIP")
    void testLockAndAssignTrip() {
        Trip trip = new Trip(0, testPassenger.getId(), null, null, 1L,
                4.051056, 9.767869, "Akwa Center",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("5.50"), 15, TripStatus.REQUESTED, 1);
        tripDAO.save(trip);
        createdTripIds.add(trip.getId());

        // Driver accepts trip
        boolean assigned = tripDAO.lockAndAssignTrip(trip.getId(), testDriver.getId(), testVehicle.getId());
        assertTrue(assigned, "Trip assignment should succeed for online driver");

        Trip assignedTrip = tripDAO.findById(trip.getId()).orElseThrow();
        assertEquals(TripStatus.ASSIGNED, assignedTrip.getStatus());
        assertEquals(testDriver.getId(), assignedTrip.getDriverId());
        assertEquals(testVehicle.getId(), assignedTrip.getVehicleId());

        Driver updatedDriver = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(DriverAvailability.ON_TRIP, updatedDriver.getAvailability());

        // Second driver attempt on already assigned trip should fail
        boolean secondAttempt = tripDAO.lockAndAssignTrip(trip.getId(), testDriver.getId(), testVehicle.getId());
        assertFalse(secondAttempt, "Cannot assign trip that is no longer REQUESTED");
    }

    @Test
    @DisplayName("Trip queries by passenger ID and driver ID")
    void testFindByPassengerAndDriver() {
        List<Trip> passengerTrips = tripDAO.findByPassengerId(4L); // Alice
        assertFalse(passengerTrips.isEmpty());
        assertTrue(passengerTrips.stream().allMatch(t -> t.getPassengerId() == 4L));

        List<Trip> driverTrips = tripDAO.findByDriverId(2L); // Samuel
        assertFalse(driverTrips.isEmpty());
        assertTrue(driverTrips.stream().allMatch(t -> t.getDriverId() != null && t.getDriverId() == 2L));

        int completedCount = tripDAO.countByStatus(TripStatus.COMPLETED);
        assertTrue(completedCount >= 5);
    }
}

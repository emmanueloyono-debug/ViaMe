package com.viame.integration;

import com.viame.dao.DBConnection;
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-CONC-01: 5 drivers accept the same REQUESTED trip at the same instant.
 * Expected: exactly one succeeds (pessimistic SELECT ... FOR UPDATE), four are rejected,
 * the trip ends ASSIGNED to the winner and only the winner is ON_TRIP.
 *
 * <p>Integration test against the real MySQL {@code viame_db}. It is <b>skipped</b>
 * (not failed) when the database is unreachable. Creates its own isolated fixture and
 * removes it afterwards.</p>
 */
@DisplayName("TC-CONC-01 Concurrent Double-Acceptance (MySQL)")
public class ConcurrencyRaceConditionTest {

    private static final int DRIVER_COUNT = 5;

    private final UserDAOImpl userDAO = new UserDAOImpl();
    private final VehicleDAOImpl vehicleDAO = new VehicleDAOImpl();
    private final TripDAOImpl tripDAO = new TripDAOImpl();

    private final List<Driver> drivers = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();
    private Passenger passenger;
    private Trip trip;

    @BeforeEach
    public void setUp() {
        Assumptions.assumeTrue(databaseAvailable(), "MySQL viame_db not reachable - skipping TC-CONC-01");

        String run = Long.toString(System.nanoTime() % 100_000_000L);
        passenger = new Passenger(0, "conc.pass." + run + "@viame.test", phone(run, 9), "hash", "salt",
                UserStatus.ACTIVE, "Concurrency Passenger", PaymentMethod.CASH, BigDecimal.ZERO);
        userDAO.save(passenger);

        for (int i = 0; i < DRIVER_COUNT; i++) {
            Driver d = new Driver(0, "conc.drv" + i + "." + run + "@viame.test", phone(run, i), "hash", "salt",
                    UserStatus.ACTIVE, "Concurrency Driver " + i, "CONC-" + run + "-" + i,
                    DriverVerificationStatus.APPROVED, new BigDecimal("5.00"), DriverAvailability.ONLINE,
                    4.05, 9.76, BigDecimal.ZERO);
            userDAO.save(d);
            drivers.add(d);
            vehicles.add(vehicleDAO.save(new Vehicle(0, d.getId(), "CC " + run.substring(0, 3) + i + " T" + (char) ('A' + i),
                    "Toyota", "Yaris", 4, VehicleStatus.ACTIVE)));
        }

        trip = tripDAO.save(new Trip(0, passenger.getId(), null, null, activePricingRuleId(),
                4.051056, 9.767869, "Akwa", 4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("8.20"), 22, TripStatus.REQUESTED, 1));
    }

    @Test
    @DisplayName("Exactly one of 5 simultaneous drivers wins the trip")
    public void testOnlyOneDriverCanAcceptTrip() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(DRIVER_COUNT);
        CountDownLatch ready = new CountDownLatch(DRIVER_COUNT);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < DRIVER_COUNT; i++) {
            long driverId = drivers.get(i).getId();
            long vehicleId = vehicles.get(i).getId();
            Callable<Boolean> attempt = () -> {
                ready.countDown();
                go.await();                       // all threads fire at the same instant
                return new TripDAOImpl().lockAndAssignTrip(trip.getId(), driverId, vehicleId);
            };
            results.add(pool.submit(attempt));
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "Driver threads failed to start");
        go.countDown();

        int winners = 0;
        long winnerId = -1;
        for (int i = 0; i < DRIVER_COUNT; i++) {
            if (results.get(i).get(30, TimeUnit.SECONDS)) {
                winners++;
                winnerId = drivers.get(i).getId();
            }
        }
        pool.shutdownNow();

        assertEquals(1, winners, "Exactly one driver must win the trip");

        Trip persisted = tripDAO.findById(trip.getId()).orElseThrow();
        assertEquals(TripStatus.ASSIGNED, persisted.getStatus());
        assertEquals(winnerId, persisted.getDriverId());

        assertEquals(1, countDriversOnTrip(), "Only the winning driver may be ON_TRIP");
    }

    @AfterEach
    public void tearDown() {
        if (!databaseAvailable()) return;
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            if (trip != null) {
                exec(conn, "DELETE FROM ride WHERE id = ?", trip.getId());
            }
            for (Driver d : drivers) {
                exec(conn, "DELETE FROM audit_log WHERE user_id = ?", d.getId());
                exec(conn, "DELETE FROM `user` WHERE id = ?", d.getId());   // cascades profile + vehicle
            }
            if (passenger != null && passenger.getId() > 0) {
                exec(conn, "DELETE FROM `user` WHERE id = ?", passenger.getId());
            }
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private int countDriversOnTrip() throws Exception {
        int count = 0;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT availability FROM driver_profile WHERE user_id = ?")) {
            for (Driver d : drivers) {
                stmt.setLong(1, d.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && "ON_TRIP".equals(rs.getString(1))) count++;
                }
            }
        }
        return count;
    }

    private static long activePricingRuleId() {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id FROM pricing_rule ORDER BY active DESC, id LIMIT 1");
             ResultSet rs = stmt.executeQuery()) {
            Assumptions.assumeTrue(rs.next(), "No pricing_rule rows - load database/schema.sql first");
            return rs.getLong(1);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean databaseAvailable() {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM ride LIMIT 1")) {
            stmt.executeQuery().close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void exec(Connection conn, String sql, long id) throws java.sql.SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private static String phone(String run, int index) {
        String digits = String.format("%07d", Long.parseLong(run) % 10_000_000L);
        return "+2376" + digits + index;
    }
}


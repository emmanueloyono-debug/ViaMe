package com.viame.dao;

import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.model.Driver;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.UserStatus;
import com.viame.model.enums.VehicleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DriverDAO Database Integration Tests")
class DriverDAOTest {

    private DriverDAO driverDAO;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;
    private Driver testDriver;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        driverDAO = new DriverDAOImpl();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testDriver = new Driver(0, "driver.dao." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "DAO Driver " + tag,
                "LIC-DAO-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, new BigDecimal("1000.00"));
        userDAO.save(testDriver);

        testVehicle = new Vehicle(0, testDriver.getId(), "LT " + tag.substring(0, 3) + " ZZ",
                "Toyota", "Corolla", 4, VehicleStatus.ACTIVE);
        vehicleDAO.save(testVehicle);
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null && testDriver.getId() > 0) {
            try {
                if (testVehicle != null && testVehicle.getId() > 0) {
                    vehicleDAO.delete(testVehicle.getId());
                }
                userDAO.delete(testDriver.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Find driver by ID for seed and created drivers")
    void testFindById() {
        // Seed driver Samuel Eto'o (ID 2)
        Optional<Driver> samuel = driverDAO.findById(2L);
        assertTrue(samuel.isPresent());
        assertEquals("Samuel Eto'o", samuel.get().getFullName());
        assertEquals("CMR-LT-2023-8891", samuel.get().getLicenceNumber());

        // Created driver
        Optional<Driver> created = driverDAO.findById(testDriver.getId());
        assertTrue(created.isPresent());
        assertEquals(testDriver.getFullName(), created.get().getFullName());

        // Non-driver or non-existent
        assertTrue(driverDAO.findById(1L).isEmpty()); // Admin is not driver
        assertTrue(driverDAO.findById(9999999L).isEmpty());
    }

    @Test
    @DisplayName("Find available drivers in bounding box")
    void testFindAvailableInBoundingBox() {
        // Bounding box around Douala: lat 4.00 to 4.10, lng 9.70 to 9.80
        List<Driver> drivers = driverDAO.findAvailableInBoundingBox(4.00, 4.10, 9.70, 9.80);
        assertNotNull(drivers);
        assertFalse(drivers.isEmpty(), "Should find online drivers in Douala center");
        assertTrue(drivers.stream().anyMatch(d -> d.getId() == testDriver.getId()),
                "Created online driver should be included in bounding box");

        // Remote bounding box (e.g. Yaounde lat ~3.8, lng ~11.5)
        List<Driver> remote = driverDAO.findAvailableInBoundingBox(3.00, 3.90, 11.00, 12.00);
        assertFalse(remote.stream().anyMatch(d -> d.getId() == testDriver.getId()));
    }

    @Test
    @DisplayName("Find drivers by verification status and count online drivers")
    void testVerificationStatusAndOnlineCount() {
        List<Driver> approved = driverDAO.findByVerificationStatus(DriverVerificationStatus.APPROVED);
        assertNotNull(approved);
        assertTrue(approved.size() >= 3); // Samuel, Eric, and testDriver

        int onlineCount = driverDAO.countOnlineDrivers();
        assertTrue(onlineCount >= 1, "There should be at least one online driver");
    }

    @Test
    @DisplayName("Update availability, location, verification status and credit earnings")
    void testDriverUpdates() throws Exception {
        // 1. Update availability to OFFLINE
        boolean availUpdated = driverDAO.updateAvailability(testDriver.getId(), DriverAvailability.OFFLINE);
        assertTrue(availUpdated);
        Driver d1 = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(DriverAvailability.OFFLINE, d1.getAvailability());

        // 2. Update location
        boolean locUpdated = driverDAO.updateLocation(testDriver.getId(), 4.080000, 9.750000);
        assertTrue(locUpdated);
        Driver d2 = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(4.080000, d2.getCurrentLat(), 0.0001);
        assertEquals(9.750000, d2.getCurrentLng(), 0.0001);

        // 3. Credit earnings and update verification status
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            driverDAO.creditEarnings(conn, testDriver.getId(), new BigDecimal("5000.00"));
            driverDAO.updateVerificationStatus(conn, testDriver.getId(), DriverVerificationStatus.REJECTED);
        }

        Driver d3 = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("6000.00").compareTo(d3.getTotalEarnings()));
        assertEquals(DriverVerificationStatus.REJECTED, d3.getVerificationStatus());
    }
}

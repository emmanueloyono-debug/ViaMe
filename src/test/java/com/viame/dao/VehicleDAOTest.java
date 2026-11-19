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

@DisplayName("VehicleDAO Database Integration Tests")
class VehicleDAOTest {

    private VehicleDAO vehicleDAO;
    private UserDAO userDAO;
    private Driver testDriver;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        vehicleDAO = new VehicleDAOImpl();
        userDAO = new UserDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testDriver = new Driver(0, "driver.veh." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Vehicle Test Driver " + tag,
                "LIC-VEH-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(testDriver);

        testVehicle = new Vehicle(0, testDriver.getId(), "CE " + tag.substring(0, 3) + " XY",
                "Nissan", "Sentra", 4, VehicleStatus.ACTIVE);
        vehicleDAO.save(testVehicle);
    }

    @AfterEach
    void tearDown() {
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
    }

    @Test
    @DisplayName("Find vehicle by ID and by Driver ID")
    void testFindByIdAndDriverId() {
        // Find seed vehicle (ID 1 belongs to Samuel ID 2)
        Optional<Vehicle> seedVeh = vehicleDAO.findById(1L);
        assertTrue(seedVeh.isPresent());
        assertEquals("LT 452 AB", seedVeh.get().getPlateNumber());
        assertEquals(2L, seedVeh.get().getDriverId());

        // Find by driver ID
        Optional<Vehicle> byDriver = vehicleDAO.findByDriverId(2L);
        assertTrue(byDriver.isPresent());
        assertEquals(1L, byDriver.get().getId());

        // Dynamic test vehicle
        Optional<Vehicle> found = vehicleDAO.findById(testVehicle.getId());
        assertTrue(found.isPresent());
        assertEquals(testVehicle.getPlateNumber(), found.get().getPlateNumber());
        assertEquals("Nissan", found.get().getMake());
        assertEquals(4, found.get().getSeats());
    }

    @Test
    @DisplayName("List all vehicles and plate existence check")
    void testFindAllAndPlateExists() {
        List<Vehicle> all = vehicleDAO.findAll();
        assertNotNull(all);
        assertTrue(all.size() >= 3); // 2 seeds + 1 test

        assertTrue(vehicleDAO.existsByPlate("LT 452 AB"));
        assertTrue(vehicleDAO.existsByPlate(testVehicle.getPlateNumber()));
        assertFalse(vehicleDAO.existsByPlate("NON-EXISTENT-PLATE-999"));
    }

    @Test
    @DisplayName("Update vehicle details and status by driver")
    void testUpdateVehicle() throws Exception {
        testVehicle.setMake("Toyota");
        testVehicle.setModel("Camry");
        testVehicle.setSeats(5);
        testVehicle.setStatus(VehicleStatus.INACTIVE);
        boolean updated = vehicleDAO.update(testVehicle);
        assertTrue(updated);

        Vehicle fetched = vehicleDAO.findById(testVehicle.getId()).orElseThrow();
        assertEquals("Toyota", fetched.getMake());
        assertEquals("Camry", fetched.getModel());
        assertEquals(5, fetched.getSeats());
        assertEquals(VehicleStatus.INACTIVE, fetched.getStatus());

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            boolean statusUpdated = vehicleDAO.updateStatusByDriver(conn, testDriver.getId(), VehicleStatus.ACTIVE);
            assertTrue(statusUpdated);
        }

        Vehicle reFetched = vehicleDAO.findById(testVehicle.getId()).orElseThrow();
        assertEquals(VehicleStatus.ACTIVE, reFetched.getStatus());
    }
}

package com.viame.dao;

import com.viame.dao.impl.UserDAOImpl;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserDAO Database Integration Tests")
class UserDAOTest {

    private UserDAO userDAO;
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userDAO = new UserDAOImpl();
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdUserIds) {
            try {
                userDAO.delete(id);
            } catch (Exception ignored) {
            }
        }
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM `user` WHERE email LIKE '%.test'")) {
            stmt.executeUpdate();
        } catch (Exception ignored) {
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Find seed users by ID, Email, and Phone")
    void testFindSeedUsers() {
        // Alice Mengue (ID 4)
        Optional<User> userOpt = userDAO.findById(4L);
        assertTrue(userOpt.isPresent(), "Seed passenger Alice (ID 4) should exist");
        User user = userOpt.get();
        assertEquals("passenger.alice@viame.cm", user.getEmail());
        assertEquals("+237670000004", user.getPhone());
        assertEquals(UserRole.PASSENGER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertInstanceOf(Passenger.class, user);
        Passenger p = (Passenger) user;
        assertEquals("Alice Mengue", p.getFullName());

        // Find by email
        Optional<User> byEmail = userDAO.findByEmail("passenger.alice@viame.cm");
        assertTrue(byEmail.isPresent());
        assertEquals(4L, byEmail.get().getId());

        // Find by phone
        Optional<User> byPhone = userDAO.findByPhone("+237670000004");
        assertTrue(byPhone.isPresent());
        assertEquals(4L, byPhone.get().getId());

        // Samuel Eto'o (ID 2, Driver)
        Optional<User> driverOpt = userDAO.findById(2L);
        assertTrue(driverOpt.isPresent());
        assertInstanceOf(Driver.class, driverOpt.get());
        Driver driver = (Driver) driverOpt.get();
        assertEquals("Samuel Eto'o", driver.getFullName());
        assertEquals("CMR-LT-2023-8891", driver.getLicenceNumber());
        assertEquals(DriverVerificationStatus.APPROVED, driver.getVerificationStatus());
    }

    @Test
    @DisplayName("List all users returns non-empty list containing seed users")
    void testFindAll() {
        List<User> all = userDAO.findAll();
        assertNotNull(all);
        assertTrue(all.size() >= 5, "Database must contain at least 5 seed users");
        assertTrue(all.stream().anyMatch(u -> u.getEmail().equals("admin@viame.cm")));
        assertTrue(all.stream().anyMatch(u -> u.getEmail().equals("passenger.alice@viame.cm")));
    }

    @Test
    @DisplayName("Existence checks by email and phone")
    void testExistsByEmailAndPhone() {
        assertTrue(userDAO.existsByEmail("admin@viame.cm"));
        assertTrue(userDAO.existsByPhone("+237670000001"));

        String nonExistent = "non.existent." + System.nanoTime() + "@viame.test";
        assertFalse(userDAO.existsByEmail(nonExistent));
        assertFalse(userDAO.existsByPhone("+237699999999"));
    }

    @Test
    @DisplayName("Save and retrieve Passenger profile")
    void testSavePassenger() {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "test.pass." + tag + "@viame.test";
        String phone = "+2376" + String.format("%08d", Long.parseLong(tag));

        Passenger passenger = new Passenger(0, email, phone, "hashVal", "saltVal",
                UserStatus.ACTIVE, "Test Passenger " + tag, PaymentMethod.WALLET, new BigDecimal("1000.00"));

        User saved = userDAO.save(passenger);
        assertNotNull(saved);
        assertTrue(saved.getId() > 0);
        createdUserIds.add(saved.getId());

        Optional<User> fetched = userDAO.findById(saved.getId());
        assertTrue(fetched.isPresent());
        assertInstanceOf(Passenger.class, fetched.get());
        Passenger fetchedP = (Passenger) fetched.get();
        assertEquals("Test Passenger " + tag, fetchedP.getFullName());
        assertEquals(PaymentMethod.WALLET, fetchedP.getDefaultPaymentMethod());
        assertEquals(new BigDecimal("1000.00"), fetchedP.getWalletBalance());
    }

    @Test
    @DisplayName("Save and retrieve Driver profile")
    void testSaveDriver() {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "test.drv." + tag + "@viame.test";
        String phone = "+2376" + String.format("%08d", Long.parseLong(tag));

        Driver driver = new Driver(0, email, phone, "hashVal", "saltVal",
                UserStatus.ACTIVE, "Test Driver " + tag, "LIC-" + tag,
                DriverVerificationStatus.PENDING, new BigDecimal("5.00"),
                DriverAvailability.ONLINE, 4.05, 9.76, BigDecimal.ZERO);

        User saved = userDAO.save(driver);
        assertNotNull(saved);
        assertTrue(saved.getId() > 0);
        createdUserIds.add(saved.getId());

        Optional<User> fetched = userDAO.findById(saved.getId());
        assertTrue(fetched.isPresent());
        assertInstanceOf(Driver.class, fetched.get());
        Driver fetchedD = (Driver) fetched.get();
        assertEquals("Test Driver " + tag, fetchedD.getFullName());
        assertEquals("LIC-" + tag, fetchedD.getLicenceNumber());
        assertEquals(DriverVerificationStatus.PENDING, fetchedD.getVerificationStatus());
        assertEquals(DriverAvailability.ONLINE, fetchedD.getAvailability());
    }

    @Test
    @DisplayName("Update user status and profile fields")
    void testUpdateUserAndStatus() throws Exception {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "test.upd." + tag + "@viame.test";
        String phone = "+2376" + String.format("%08d", Long.parseLong(tag));

        Passenger passenger = new Passenger(0, email, phone, "hash1", "salt1",
                UserStatus.ACTIVE, "Original Name", PaymentMethod.CASH, BigDecimal.ZERO);
        userDAO.save(passenger);
        createdUserIds.add(passenger.getId());

        // Update fields
        passenger.setFullName("Updated Name");
        passenger.setDefaultPaymentMethod(PaymentMethod.WALLET);
        passenger.setStatus(UserStatus.SUSPENDED);
        boolean updated = userDAO.update(passenger);
        assertTrue(updated);

        Optional<User> fetched = userDAO.findById(passenger.getId());
        assertTrue(fetched.isPresent());
        Passenger fetchedP = (Passenger) fetched.get();
        assertEquals("Updated Name", fetchedP.getFullName());
        assertEquals(PaymentMethod.WALLET, fetchedP.getDefaultPaymentMethod());
        assertEquals(UserStatus.SUSPENDED, fetchedP.getStatus());

        // Delete user
        boolean deleted = userDAO.delete(passenger.getId());
        assertTrue(deleted);
        createdUserIds.remove(passenger.getId());
        assertTrue(userDAO.findById(passenger.getId()).isEmpty());
    }
}

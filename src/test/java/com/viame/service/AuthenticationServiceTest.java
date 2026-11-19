package com.viame.service;

import com.viame.dao.DBConnection;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.AuthenticationException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserStatus;
import com.viame.model.enums.VehicleStatus;
import com.viame.util.PasswordHasher;
import com.viame.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthenticationService Integration and Unit Tests")
class AuthenticationServiceTest {

    private AuthenticationService authService;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdVehicleIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();
        SessionManager.getInstance().logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            for (Long vehId : createdVehicleIds) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM vehicle WHERE id = ?")) {
                    stmt.setLong(1, vehId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
            for (Long uid : createdUserIds) {
                try {
                    userDAO.delete(uid);
                } catch (Exception ignored) {
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM `user` WHERE email LIKE '%.test'")) {
                stmt.executeUpdate();
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        createdVehicleIds.clear();
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Login with valid credentials authenticates user and sets active session")
    void testLoginSuccess() throws Exception {
        User user = authService.login("admin@viame.cm", "Password123!");
        assertNotNull(user);
        assertEquals("admin@viame.cm", user.getEmail());
        assertTrue(SessionManager.getInstance().getCurrentUser().isPresent());
        assertEquals(user.getId(), SessionManager.getInstance().getCurrentUser().get().getId());
    }

    @Test
    @DisplayName("Login rejects empty, wrong password, or non-existent users")
    void testLoginFailures() {
        assertThrows(AuthenticationException.class, () -> authService.login("", "Password123!"));
        assertThrows(AuthenticationException.class, () -> authService.login("admin@viame.cm", ""));
        assertThrows(AuthenticationException.class, () -> authService.login("admin@viame.cm", "WrongPassword!"));
        assertThrows(AuthenticationException.class, () -> authService.login("nobody@viame.cm", "Password123!"));
    }

    @Test
    @DisplayName("Login rejects suspended user accounts")
    void testLoginSuspendedUser() {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "susp." + tag + "@viame.test";
        String phone = "+2376" + String.format("%08d", Long.parseLong(tag));
        String hash = PasswordHasher.hash("Password123!");
        String salt = PasswordHasher.extractSalt(hash);

        Passenger suspended = new Passenger(0, email, phone, hash, salt,
                UserStatus.SUSPENDED, "Suspended User", PaymentMethod.CASH, java.math.BigDecimal.ZERO);
        userDAO.save(suspended);
        createdUserIds.add(suspended.getId());

        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                authService.login(email, "Password123!"));
        assertTrue(ex.getMessage().contains("suspended"));
    }

    @Test
    @DisplayName("Logout clears session context")
    void testLogout() throws Exception {
        authService.login("admin@viame.cm", "Password123!");
        assertTrue(SessionManager.getInstance().getCurrentUser().isPresent());

        authService.logout();
        assertTrue(SessionManager.getInstance().getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("Register passenger with validation checks and duplicate rejection")
    void testRegisterPassenger() {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "reg.pass." + tag + "@viame.test";
        String phone = "6" + String.format("%08d", Long.parseLong(tag));

        // Invalid credentials checks
        assertThrows(ValidationException.class, () ->
                authService.registerPassenger("bad-email", phone, "Password123!", "Name", PaymentMethod.WALLET));
        assertThrows(ValidationException.class, () ->
                authService.registerPassenger(email, "12345", "Password123!", "Name", PaymentMethod.WALLET));
        assertThrows(ValidationException.class, () ->
                authService.registerPassenger(email, phone, "short", "Name", PaymentMethod.WALLET));

        // Valid registration
        Passenger passenger = authService.registerPassenger(email, phone, "Password123!", "New Passenger", PaymentMethod.WALLET);
        assertNotNull(passenger);
        assertTrue(passenger.getId() > 0);
        createdUserIds.add(passenger.getId());

        // Duplicate registration attempts
        assertThrows(ValidationException.class, () ->
                authService.registerPassenger(email, "+237699999999", "Password123!", "Dup", PaymentMethod.WALLET));
        assertThrows(ValidationException.class, () ->
                authService.registerPassenger("other." + tag + "@viame.test", phone, "Password123!", "Dup", PaymentMethod.WALLET));
    }

    @Test
    @DisplayName("Register driver with vehicle creation and pending status")
    void testRegisterDriver() {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "reg.drv." + tag + "@viame.test";
        String phone = "6" + String.format("%08d", Long.parseLong(tag));
        String plate = "LT " + tag.substring(0, 3) + " AD";

        // Invalid licence / vehicle plate
        assertThrows(ValidationException.class, () ->
                authService.registerDriver(email, phone, "Password123!", "Name", "bad licence", plate, "Toyota", "Corolla", 4));
        assertThrows(ValidationException.class, () ->
                authService.registerDriver(email, phone, "Password123!", "Name", "CMR-LT-2026-1234", "invalid plate", "Toyota", "Corolla", 4));
        assertThrows(ValidationException.class, () ->
                authService.registerDriver(email, phone, "Password123!", "Name", "CMR-LT-2026-1234", plate, "Toyota", "Corolla", 0));

        // Valid registration
        Driver driver = authService.registerDriver(email, phone, "Password123!", "New Driver",
                "CMR-LT-2026-1234", plate, "Toyota", "Corolla", 4);
        assertNotNull(driver);
        assertTrue(driver.getId() > 0);
        assertEquals(DriverVerificationStatus.PENDING, driver.getVerificationStatus());
        createdUserIds.add(driver.getId());

        Vehicle vehicle = vehicleDAO.findByDriverId(driver.getId()).orElseThrow();
        createdVehicleIds.add(vehicle.getId());
        assertEquals(plate, vehicle.getPlateNumber());
        assertEquals(VehicleStatus.PENDING, vehicle.getStatus());
    }

    @Test
    @DisplayName("Change password verifies old password and updates hash")
    void testChangePassword() throws Exception {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        String email = "cp." + tag + "@viame.test";
        String phone = "6" + String.format("%08d", Long.parseLong(tag));

        Passenger passenger = authService.registerPassenger(email, phone, "OldPassword123!", "CP User", PaymentMethod.CASH);
        createdUserIds.add(passenger.getId());

        // Wrong old password
        assertThrows(AuthenticationException.class, () ->
                authService.changePassword(passenger.getId(), "WrongOld!", "NewPassword123!"));

        // Weak new password
        assertThrows(ValidationException.class, () ->
                authService.changePassword(passenger.getId(), "OldPassword123!", "weak"));

        // Valid change
        authService.changePassword(passenger.getId(), "OldPassword123!", "NewPassword123!");

        // Verify login works with new password and fails with old
        assertThrows(AuthenticationException.class, () -> authService.login(email, "OldPassword123!"));
        User loggedIn = authService.login(email, "NewPassword123!");
        assertNotNull(loggedIn);
    }
}

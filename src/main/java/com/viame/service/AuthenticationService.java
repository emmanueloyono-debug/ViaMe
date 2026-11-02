package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.TransactionManager;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.AuditLogDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.AuthenticationException;
import com.viame.exception.EntityNotFoundException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserStatus;
import com.viame.model.enums.VehicleStatus;
import com.viame.util.InputValidator;
import com.viame.util.PasswordHasher;
import com.viame.util.SessionManager;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Authentication and User Registration Service (UC01 / AD-2).
 * Handles BCrypt password hashing, verification, session management, and role-based registration.
 */
public class AuthenticationService {

    private final UserDAO userDAO;
    private final VehicleDAO vehicleDAO;
    private final AuditLogDAO auditLogDAO;
    private final TransactionManager transactionManager;

    public AuthenticationService() {
        this(new UserDAOImpl(), new VehicleDAOImpl(), new AuditLogDAOImpl(), new TransactionManager());
    }

    public AuthenticationService(UserDAO userDAO, VehicleDAO vehicleDAO, AuditLogDAO auditLogDAO, TransactionManager transactionManager) {
        this.userDAO = userDAO;
        this.vehicleDAO = vehicleDAO;
        this.auditLogDAO = auditLogDAO;
        this.transactionManager = transactionManager;
    }

    /**
     * Authenticates a user by email and password, setting the global session on success.
     *
     * @param email         raw email string
     * @param plainPassword plain text password
     * @return authenticated User instance
     * @throws AuthenticationException if credentials are invalid or account is suspended
     */
    public User login(String email, String plainPassword) throws AuthenticationException {
        if (email == null || email.isBlank() || plainPassword == null || plainPassword.isBlank()) {
            throw new AuthenticationException("Email and password are required");
        }

        String normalizedEmail = InputValidator.normalizeEmail(email);
        User user = userDAO.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!PasswordHasher.verify(plainPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthenticationException("Your account has been suspended. Please contact administration.");
        }

        SessionManager.getInstance().login(user);
        auditLogDAO.log(user.getId(), "LOGIN", "user", user.getId());
        return user;
    }

    /**
     * Logs out the currently authenticated user and clears the session context.
     */
    public void logout() {
        Optional<User> currentUser = SessionManager.getInstance().getCurrentUser();
        currentUser.ifPresent(user -> auditLogDAO.log(user.getId(), "LOGOUT", "user", user.getId()));
        SessionManager.getInstance().logout();
    }

    /**
     * Registers a new passenger profile.
     */
    public Passenger registerPassenger(String email, String phone, String plainPassword,
                                       String fullName, PaymentMethod defaultPaymentMethod) {
        validateCredentials(email, phone, plainPassword);
        String name = InputValidator.requireNonBlank(fullName, "Full name");

        String normalizedEmail = InputValidator.normalizeEmail(email);
        String normalizedPhone = InputValidator.normalizePhone(phone);

        if (userDAO.existsByEmail(normalizedEmail)) {
            throw new ValidationException("An account with email " + normalizedEmail + " already exists");
        }
        if (userDAO.existsByPhone(normalizedPhone)) {
            throw new ValidationException("An account with phone number " + normalizedPhone + " already exists");
        }

        String hash = PasswordHasher.hash(plainPassword);
        String salt = PasswordHasher.extractSalt(hash);

        Passenger passenger = new Passenger(0, normalizedEmail, normalizedPhone, hash, salt,
                UserStatus.ACTIVE, name,
                defaultPaymentMethod != null ? defaultPaymentMethod : PaymentMethod.CASH,
                BigDecimal.ZERO);

        return transactionManager.inTransaction(conn -> {
            userDAO.insertUser(conn, passenger);
            userDAO.insertPassengerProfile(conn, passenger);
            auditLogDAO.log(conn, passenger.getId(), "REGISTER_PASSENGER", "user", passenger.getId());
            return passenger;
        });
    }

    /**
     * Registers a new driver application along with their vehicle details (AD-2 workflow).
     * Verification status starts as PENDING until approved by an administrator.
     */
    public Driver registerDriver(String email, String phone, String plainPassword,
                                 String fullName, String licenceNumber,
                                 String plateNumber, String make, String model, int seats) {
        validateCredentials(email, phone, plainPassword);
        String name = InputValidator.requireNonBlank(fullName, "Full name");
        String licence = InputValidator.requireNonBlank(licenceNumber, "Licence number");
        InputValidator.require(InputValidator.isValidLicence(licence), "Invalid licence number format");

        String plate = InputValidator.normalizePlate(plateNumber);
        InputValidator.require(InputValidator.isValidPlate(plate), "Invalid vehicle plate number (e.g. LT 452 AB)");

        String vMake = InputValidator.requireNonBlank(make, "Vehicle make");
        String vModel = InputValidator.requireNonBlank(model, "Vehicle model");
        InputValidator.require(seats >= 1 && seats <= 15, "Seats must be between 1 and 15");

        String normalizedEmail = InputValidator.normalizeEmail(email);
        String normalizedPhone = InputValidator.normalizePhone(phone);

        if (userDAO.existsByEmail(normalizedEmail)) {
            throw new ValidationException("An account with email " + normalizedEmail + " already exists");
        }
        if (userDAO.existsByPhone(normalizedPhone)) {
            throw new ValidationException("An account with phone number " + normalizedPhone + " already exists");
        }
        if (vehicleDAO.existsByPlate(plate)) {
            throw new ValidationException("Vehicle with plate " + plate + " is already registered");
        }

        String hash = PasswordHasher.hash(plainPassword);
        String salt = PasswordHasher.extractSalt(hash);

        Driver driver = new Driver(0, normalizedEmail, normalizedPhone, hash, salt,
                UserStatus.ACTIVE, name, licence,
                DriverVerificationStatus.PENDING, BigDecimal.valueOf(5.00),
                DriverAvailability.OFFLINE, 4.051056, 9.767869, BigDecimal.ZERO);

        return transactionManager.inTransaction(conn -> {
            userDAO.insertUser(conn, driver);
            userDAO.insertDriverProfile(conn, driver);

            Vehicle vehicle = new Vehicle(0, driver.getId(), plate, vMake, vModel, seats, VehicleStatus.PENDING);
            vehicleDAO.insert(conn, vehicle);

            auditLogDAO.log(conn, driver.getId(), "REGISTER_DRIVER_PENDING", "driver_profile", driver.getId());
            return driver;
        });
    }

    /**
     * Updates an existing user's password.
     */
    public void changePassword(long userId, String oldPassword, String newPassword) throws AuthenticationException {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));

        if (!PasswordHasher.verify(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Current password is incorrect");
        }
        InputValidator.require(InputValidator.isStrongPassword(newPassword),
                "New password must be at least 8 characters with at least one letter and one number");

        String newHash = PasswordHasher.hash(newPassword);
        String newSalt = PasswordHasher.extractSalt(newHash);
        user.setPasswordHash(newHash);
        user.setPasswordSalt(newSalt);

        userDAO.update(user);
        auditLogDAO.log(userId, "PASSWORD_CHANGE", "user", userId);
    }

    private void validateCredentials(String email, String phone, String plainPassword) {
        InputValidator.require(InputValidator.isValidEmail(email), "Invalid email format");
        InputValidator.require(InputValidator.isValidPhone(phone), "Invalid phone number format (must be 9 digits starting with 6)");
        InputValidator.require(InputValidator.isStrongPassword(plainPassword),
                "Password must be at least 8 characters with at least one letter and one number");
    }
}


package com.viame.service;

import com.viame.dao.DBConnection;
import com.viame.dao.DriverDAO;
import com.viame.dao.FareDAO;
import com.viame.dao.PassengerDAO;
import com.viame.dao.PaymentDAO;
import com.viame.dao.TripDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.FareDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.PaymentDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Fare;
import com.viame.model.Passenger;
import com.viame.model.Payment;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.PaymentStatus;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentService Integration Tests")
class PaymentServiceTest {

    private PaymentService paymentService;
    private PassengerDAO passengerDAO;
    private DriverDAO driverDAO;
    private TripDAO tripDAO;
    private FareDAO fareDAO;
    private PaymentDAO paymentDAO;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;

    private Passenger testPassenger;
    private Driver testDriver;
    private Vehicle testVehicle;
    private Trip testTrip;
    private Fare testFare;

    @BeforeEach
    void setUp() throws Exception {
        paymentService = new PaymentService();
        passengerDAO = new PassengerDAOImpl();
        driverDAO = new DriverDAOImpl();
        tripDAO = new TripDAOImpl();
        fareDAO = new FareDAOImpl();
        paymentDAO = new PaymentDAOImpl();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        testPassenger = new Passenger(0, "pay.svc.pass." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Payment Svc Passenger",
                PaymentMethod.WALLET, new BigDecimal("10000.00"));
        userDAO.save(testPassenger);

        testDriver = new Driver(0, "pay.svc.drv." + tag + "@viame.test",
                "+2376" + String.format("%08d", (Long.parseLong(tag) + 1) % 100000000),
                "hash", "salt", UserStatus.ACTIVE, "Payment Svc Driver",
                "LIC-PAYSVC-" + tag, DriverVerificationStatus.APPROVED,
                new BigDecimal("5.00"), DriverAvailability.ONLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(testDriver);

        testVehicle = new Vehicle(0, testDriver.getId(), "LT " + tag.substring(0, 3) + " PY",
                "Toyota", "Yaris", 4, VehicleStatus.ACTIVE);
        vehicleDAO.save(testVehicle);

        testTrip = new Trip(0, testPassenger.getId(), testDriver.getId(), testVehicle.getId(), 1L,
                4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("5.00"), 15, TripStatus.IN_PROGRESS, 1);
        tripDAO.save(testTrip);

        testFare = new Fare(0, testTrip.getId(), new BigDecimal("500.00"),
                new BigDecimal("1000.00"), new BigDecimal("500.00"),
                new BigDecimal("1.00"), new BigDecimal("2000.00"), "XAF");
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            fareDAO.insert(conn, testFare);
        }
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            if (testTrip != null && testTrip.getId() > 0) {
                paymentDAO.deleteByRideId(conn, testTrip.getId());
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM fare WHERE ride_id = ?")) {
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
    @DisplayName("Settle trip payment with WALLET debits passenger, credits driver 80%, and marks payment PAID")
    void testSettlePaymentWallet() throws Exception {
        Payment payment = paymentService.settleTripPayment(testTrip.getId(), PaymentMethod.WALLET);
        assertNotNull(payment);
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(0, new BigDecimal("2000.00").compareTo(payment.getAmount()));
        assertEquals(PaymentMethod.WALLET, payment.getMethod());

        // Verify passenger wallet debited: 10000 - 2000 = 8000
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            BigDecimal balance = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("8000.00").compareTo(balance));
        }

        // Verify driver credited 80% of 2000 = 1600
        Driver driver = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1600.00").compareTo(driver.getTotalEarnings()));

        // Verify idempotency: settling again returns same payment record without double-debiting
        Payment duplicate = paymentService.settleTripPayment(testTrip.getId(), PaymentMethod.WALLET);
        assertEquals(payment.getId(), duplicate.getId());
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            BigDecimal balance = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("8000.00").compareTo(balance));
        }
    }

    @Test
    @DisplayName("Settle payment fails with InsufficientFundsException when wallet balance is lower than fare")
    void testSettlePaymentInsufficientFunds() throws Exception {
        String tag = Long.toString(System.nanoTime() % 1_000_000);
        Passenger brokePassenger = new Passenger(0, "broke.pay." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Broke Passenger",
                PaymentMethod.WALLET, new BigDecimal("500.00")); // only 500 XAF, fare is 2000
        userDAO.save(brokePassenger);

        Trip brokeTrip = new Trip(0, brokePassenger.getId(), testDriver.getId(), testVehicle.getId(), 1L,
                4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("5.00"), 15, TripStatus.IN_PROGRESS, 1);
        tripDAO.save(brokeTrip);

        Fare brokeFare = new Fare(0, brokeTrip.getId(), new BigDecimal("500.00"),
                new BigDecimal("1000.00"), new BigDecimal("500.00"),
                new BigDecimal("1.00"), new BigDecimal("2000.00"), "XAF");
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            fareDAO.insert(conn, brokeFare);
        }

        try {
            assertThrows(InsufficientFundsException.class, () ->
                    paymentService.settleTripPayment(brokeTrip.getId(), PaymentMethod.WALLET));
        } finally {
            try (Connection conn = DBConnection.getInstance().getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM fare WHERE ride_id = ?")) {
                    stmt.setLong(1, brokeTrip.getId());
                    stmt.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ride WHERE id = ?")) {
                    stmt.setLong(1, brokeTrip.getId());
                    stmt.executeUpdate();
                } catch (Exception ignored) {}
            }
            userDAO.delete(brokePassenger.getId());
        }
    }

    @Test
    @DisplayName("Top up wallet validates minimum amount and credits passenger balance")
    void testTopUpWallet() {
        // Below minimum topup (< 500 XAF)
        assertThrows(ValidationException.class, () ->
                paymentService.topUpWallet(testPassenger.getId(), new BigDecimal("499.00"), "MTN_MOMO"));

        // Valid topup (+5000 XAF)
        BigDecimal newBalance = paymentService.topUpWallet(testPassenger.getId(), new BigDecimal("5000.00"), "MTN_MOMO");
        assertEquals(0, new BigDecimal("15000.00").compareTo(newBalance));
    }

    @Test
    @DisplayName("Process cancellation fee debits passenger and compensates driver with 80%")
    void testProcessCancellationFee() throws Exception {
        Optional<Payment> feePayment = paymentService.processCancellationFee(testTrip.getId(),
                testPassenger.getId(), testDriver.getId(), new BigDecimal("1000.00"));
        assertTrue(feePayment.isPresent());
        assertEquals(PaymentStatus.PAID, feePayment.get().getStatus());
        assertEquals(0, new BigDecimal("1000.00").compareTo(feePayment.get().getAmount()));

        // Driver receives 80% = 800
        Driver driver = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("800.00").compareTo(driver.getTotalEarnings()));

        // Passenger balance: 10000 - 1000 = 9000
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            BigDecimal balance = passengerDAO.getWalletBalance(conn, testPassenger.getId());
            assertEquals(0, new BigDecimal("9000.00").compareTo(balance));
        }
    }
}

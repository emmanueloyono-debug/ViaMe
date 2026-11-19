package com.viame.util;

import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager State Isolation Tests")
class SessionManagerIsolationTest {

    private final Passenger passengerAlice = new Passenger(4L, "alice@viame.cm", "+237670000004", "hash", "salt",
            UserStatus.ACTIVE, "Alice Mengue", PaymentMethod.WALLET, BigDecimal.valueOf(25000.00));

    private final Driver driverSamuel = new Driver(2L, "samuel@viame.cm", "+237670000002", "hash", "salt",
            UserStatus.ACTIVE, "Samuel Eto'o", "CMR-LT-2023-8891", DriverVerificationStatus.APPROVED,
            BigDecimal.valueOf(4.85), DriverAvailability.ONLINE, 4.051056, 9.767869, BigDecimal.valueOf(142000.00));

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().clearAllSessions();
    }

    @Test
    @DisplayName("Concurrent Passenger and Driver sessions remain isolated without state collision or instanceof failure")
    void testConcurrentSessionStateIsolation() throws InterruptedException {
        int iterations = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicBoolean passengerSuccess = new AtomicBoolean(true);
        AtomicReference<String> passengerError = new AtomicReference<>();

        AtomicBoolean driverSuccess = new AtomicBoolean(true);
        AtomicReference<String> driverError = new AtomicReference<>();

        // Thread 1: Passenger simulation
        Thread passengerThread = new Thread(() -> {
            try {
                startLatch.await();
                SessionManager sm = SessionManager.getInstance();
                sm.login(passengerAlice);

                for (int i = 0; i < iterations; i++) {
                    User current = sm.getCurrentUser().orElse(null);
                    if (!(current instanceof Passenger)) {
                        passengerSuccess.set(false);
                        passengerError.set("Expected Passenger instance but got: " + current);
                        break;
                    }
                    if (sm.hasRole(UserRole.DRIVER)) {
                        passengerSuccess.set(false);
                        passengerError.set("Passenger thread has DRIVER role!");
                        break;
                    }
                    if (!sm.hasRole(UserRole.PASSENGER)) {
                        passengerSuccess.set(false);
                        passengerError.set("Passenger thread missing PASSENGER role!");
                        break;
                    }
                    Thread.sleep(2);
                }
            } catch (Exception e) {
                passengerSuccess.set(false);
                passengerError.set(e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Driver simulation
        Thread driverThread = new Thread(() -> {
            try {
                startLatch.await();
                SessionManager sm = SessionManager.getInstance();
                sm.login(driverSamuel);

                for (int i = 0; i < iterations; i++) {
                    User current = sm.getCurrentUser().orElse(null);
                    if (!(current instanceof Driver)) {
                        driverSuccess.set(false);
                        driverError.set("Expected Driver instance but got: " + current);
                        break;
                    }
                    if (sm.hasRole(UserRole.PASSENGER)) {
                        driverSuccess.set(false);
                        driverError.set("Driver thread has PASSENGER role!");
                        break;
                    }
                    if (!sm.hasRole(UserRole.DRIVER)) {
                        driverSuccess.set(false);
                        driverError.set("Driver thread missing DRIVER role!");
                        break;
                    }
                    Thread.sleep(2);
                }
            } catch (Exception e) {
                driverSuccess.set(false);
                driverError.set(e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        passengerThread.start();
        driverThread.start();
        startLatch.countDown();

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Both simulation threads should finish");
        assertTrue(passengerSuccess.get(), "Passenger simulation failed: " + passengerError.get());
        assertTrue(driverSuccess.get(), "Driver simulation failed: " + driverError.get());
    }

    @Test
    @DisplayName("Per-token session isolation allows concurrent token-based contexts")
    void testPerTokenSessionIsolation() {
        SessionManager sm = SessionManager.getInstance();
        String tokenPassenger = "token_passenger_alice_12345";
        String tokenDriver = "token_driver_samuel_67890";

        sm.login(tokenPassenger, passengerAlice);
        sm.login(tokenDriver, driverSamuel);

        assertTrue(sm.isAuthenticated(tokenPassenger));
        assertTrue(sm.isAuthenticated(tokenDriver));

        assertEquals(passengerAlice, sm.getCurrentUser(tokenPassenger).orElse(null));
        assertEquals(driverSamuel, sm.getCurrentUser(tokenDriver).orElse(null));

        assertTrue(sm.hasRole(tokenPassenger, UserRole.PASSENGER));
        assertFalse(sm.hasRole(tokenPassenger, UserRole.DRIVER));

        assertTrue(sm.hasRole(tokenDriver, UserRole.DRIVER));
        assertFalse(sm.hasRole(tokenDriver, UserRole.PASSENGER));

        assertEquals(passengerAlice, sm.requireRole(tokenPassenger, UserRole.PASSENGER));
        assertThrows(SecurityException.class, () -> sm.requireRole(tokenPassenger, UserRole.DRIVER));

        sm.logout(tokenPassenger);
        assertFalse(sm.isAuthenticated(tokenPassenger));
        assertTrue(sm.isAuthenticated(tokenDriver));
    }

    @Test
    @DisplayName("Isolated SessionManager instances operate independently")
    void testIsolatedSessionManagerInstances() {
        SessionManager managerA = SessionManager.createIsolated();
        SessionManager managerB = SessionManager.createIsolated();

        managerA.login(passengerAlice);
        managerB.login(driverSamuel);

        assertEquals(passengerAlice, managerA.getCurrentUser().orElse(null));
        assertEquals(driverSamuel, managerB.getCurrentUser().orElse(null));

        assertTrue(managerA.hasRole(UserRole.PASSENGER));
        assertFalse(managerA.hasRole(UserRole.DRIVER));

        assertTrue(managerB.hasRole(UserRole.DRIVER));
        assertFalse(managerB.hasRole(UserRole.PASSENGER));
    }

    @Test
    @DisplayName("Scoped session execution sets and restores session context cleanly")
    void testScopedSessionExecution() throws Exception {
        SessionManager sm = SessionManager.getInstance();
        sm.login(passengerAlice);

        assertEquals(passengerAlice, sm.getCurrentUser().orElse(null));

        // Execute block as driver
        sm.runWithUser(driverSamuel, () -> {
            assertEquals(driverSamuel, sm.getCurrentUser().orElse(null));
            assertTrue(sm.hasRole(UserRole.DRIVER));
        });

        // After scoped block, restored to passengerAlice
        assertEquals(passengerAlice, sm.getCurrentUser().orElse(null));
    }
}

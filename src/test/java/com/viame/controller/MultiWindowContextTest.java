package com.viame.controller;

import com.viame.MainApp;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserStatus;
import com.viame.util.SessionManager;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Multi-Window Simulation Context Tests")
class MultiWindowContextTest {

    @AfterEach
    void clearSession() {
        SessionManager.getInstance().clearAllSessions();
    }

    @Test
    @DisplayName("MainApp exposes both 4-arg and 5-arg openSimulationWindow overloads")
    void testMainAppOpenSimulationWindowOverloads() throws Exception {
        Method fourArg = MainApp.class.getMethod("openSimulationWindow",
                String.class, String.class, int.class, int.class);
        assertEquals(Stage.class, fourArg.getReturnType());

        Method fiveArg = MainApp.class.getMethod("openSimulationWindow",
                String.class, String.class, int.class, int.class, Consumer.class);
        assertEquals(Stage.class, fiveArg.getReturnType());
    }

    @Test
    @DisplayName("PassengerDashboardController preserves explicit currentPassenger when initialize() runs")
    void testPassengerSimulationBindingIsolation() {
        Passenger sessionPassenger = new Passenger(1L, "session@viame.cm", "+237670000001",
                "h", "s", UserStatus.ACTIVE, "Session Passenger", PaymentMethod.CASH, new BigDecimal("1000.00"));
        Passenger simPassenger = new Passenger(2L, "sim@viame.cm", "+237670000002",
                "h", "s", UserStatus.ACTIVE, "Sim Passenger", PaymentMethod.WALLET, new BigDecimal("5000.00"));

        SessionManager.getInstance().login(sessionPassenger);

        // When currentPassenger is already set before initialize(), initialize() must not overwrite it
        PassengerDashboardController explicitCtrl = new PassengerDashboardController();
        explicitCtrl.setPassenger(simPassenger);
        explicitCtrl.initialize();
        assertSame(simPassenger, explicitCtrl.currentPassenger);

        // When currentPassenger is null, initialize() binds from SessionManager
        PassengerDashboardController defaultCtrl = new PassengerDashboardController();
        defaultCtrl.initialize();
        assertSame(sessionPassenger, defaultCtrl.currentPassenger);

        // setPassenger after initialize() overrides with simulation passenger
        defaultCtrl.setPassenger(simPassenger);
        assertSame(simPassenger, defaultCtrl.currentPassenger);
    }

    @Test
    @DisplayName("DriverDashboardController preserves explicit currentDriver when initialize() runs")
    void testDriverSimulationBindingIsolation() {
        Driver sessionDriver = new Driver(10L, "drv1@viame.cm", "+237670000010",
                "h", "s", UserStatus.ACTIVE, "Session Driver", "LIC-1",
                DriverVerificationStatus.APPROVED, new BigDecimal("4.90"), DriverAvailability.ONLINE,
                4.05, 9.76, new BigDecimal("2000.00"));
        Driver simDriver = new Driver(20L, "drv2@viame.cm", "+237670000020",
                "h", "s", UserStatus.ACTIVE, "Sim Driver", "LIC-2",
                DriverVerificationStatus.APPROVED, new BigDecimal("4.75"), DriverAvailability.OFFLINE,
                4.06, 9.75, new BigDecimal("8000.00"));

        SessionManager.getInstance().login(sessionDriver);

        DriverDashboardController explicitCtrl = new DriverDashboardController();
        explicitCtrl.setDriver(simDriver);
        explicitCtrl.initialize();
        assertSame(simDriver, explicitCtrl.currentDriver);

        DriverDashboardController defaultCtrl = new DriverDashboardController();
        defaultCtrl.initialize();
        assertSame(sessionDriver, defaultCtrl.currentDriver);

        defaultCtrl.setDriver(simDriver);
        assertSame(simDriver, defaultCtrl.currentDriver);
    }
}

package com.viame.integration;

import com.viame.api.RestApiServer;
import com.viame.dao.DBConnection;
import com.viame.dao.DriverDAO;
import com.viame.dao.PassengerDAO;
import com.viame.dao.SessionDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.SessionDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Payment;
import com.viame.model.PricingRule;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.PaymentStatus;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.VehicleStatus;
import com.viame.model.report.ReportMath;
import com.viame.model.report.ReportRows.DailyRevenueRow;
import com.viame.service.AdminService;
import com.viame.service.FareCalculationService;
import com.viame.service.FareQuote;
import com.viame.service.PaymentService;
import com.viame.service.ReportService;
import com.viame.service.TripService;
import com.viame.util.JsonUtil;
import com.viame.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test bridging the Mobile REST API ({@link RestApiServer})
 * and Desktop Backend Services ({@link AdminService}, {@link TripService},
 * {@link PaymentService}, {@link ReportService}, DAOs) against live MySQL {@code viame_db}.
 */
@DisplayName("Mobile-to-Desktop Backend Bridge End-to-End Integration Tests")
class MobileDesktopBackendBridgeTest {

    private static final long ADMIN_ID = 1L;
    private static final long SEED_DRIVER_SAMUEL_ID = 2L;
    private static final long SEED_PASSENGER_ALICE_ID = 4L;
    private static final String SEED_PASSENGER_EMAIL = "passenger.alice@viame.cm";
    private static final String SEED_DRIVER_EMAIL = "driver.samuel@viame.cm";
    private static final String SEED_PASSWORD = "Password123!";

    private int port;
    private RestApiServer server;
    private HttpClient client;

    private AdminService adminService;
    private TripService tripService;
    private PaymentService paymentService;
    private ReportService reportService;
    private DriverDAO driverDAO;
    private PassengerDAO passengerDAO;
    private VehicleDAO vehicleDAO;
    private SessionDAO sessionDAO;

    private long baselineMaxAuditLogId;
    private long baselineMaxPricingRuleId;
    private PricingRule originalActiveRule;

    private BigDecimal originalAliceBalance;
    private BigDecimal originalSamuelEarnings;
    private BigDecimal originalSamuelRating;
    private DriverAvailability originalSamuelAvailability;
    private double originalSamuelLat;
    private double originalSamuelLng;

    private final List<String> issuedTokens = new ArrayList<>();
    private final List<Long> createdTripIds = new ArrayList<>();
    private final List<Long> createdVehicleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPricingRuleIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(databaseAvailable(), "MySQL viame_db not reachable - skipping bridge integration tests");

        adminService = new AdminService();
        tripService = new TripService();
        paymentService = new PaymentService();
        reportService = new ReportService();
        driverDAO = new DriverDAOImpl();
        passengerDAO = new PassengerDAOImpl();
        vehicleDAO = new VehicleDAOImpl();
        sessionDAO = new SessionDAOImpl();
        client = HttpClient.newHttpClient();

        snapshotSeedState();

        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new RestApiServer(port);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (!databaseAvailable()) {
            return;
        }

        for (String token : issuedTokens) {
            try {
                sessionDAO.deleteByTokenHash(RestApiServer.hashToken(token));
            } catch (Exception ignored) {
            }
        }
        issuedTokens.clear();

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            // 1. Delete trip-scoped rows (rating, payment, fare, ride)
            for (Long tripId : createdTripIds) {
                execUpdate(conn, "DELETE FROM rating WHERE ride_id = ?", tripId);
                execUpdate(conn, "DELETE FROM payment WHERE ride_id = ?", tripId);
                execUpdate(conn, "DELETE FROM fare WHERE ride_id = ?", tripId);
                execUpdate(conn, "DELETE FROM ride WHERE id = ?", tripId);
            }

            // 2. Delete audit logs created during the test
            execUpdate(conn, "DELETE FROM audit_log WHERE id > ?", baselineMaxAuditLogId);

            // 3. Delete created sessions, vehicles, profiles, and users
            for (Long userId : createdUserIds) {
                execUpdate(conn, "DELETE FROM session WHERE user_id = ?", userId);
                execUpdate(conn, "DELETE FROM vehicle WHERE driver_id = ?", userId);
            }
            for (Long vehicleId : createdVehicleIds) {
                execUpdate(conn, "DELETE FROM vehicle WHERE id = ?", vehicleId);
            }
            for (Long userId : createdUserIds) {
                execUpdate(conn, "DELETE FROM driver_profile WHERE user_id = ?", userId);
                execUpdate(conn, "DELETE FROM passenger_profile WHERE user_id = ?", userId);
                execUpdate(conn, "DELETE FROM `user` WHERE id = ?", userId);
            }

            // 4. Delete temporary pricing rules & restore original active pricing rule
            for (Long ruleId : createdPricingRuleIds) {
                execUpdate(conn, "DELETE FROM pricing_rule WHERE id = ?", ruleId);
            }
            execUpdate(conn, "DELETE FROM pricing_rule WHERE id > ?", baselineMaxPricingRuleId);
            if (originalActiveRule != null) {
                execUpdate(conn, "UPDATE pricing_rule SET active = TRUE WHERE id = ?", originalActiveRule.getId());
            }

            // 5. Restore seed passenger wallet balance & seed driver earnings/rating/availability/location
            if (originalAliceBalance != null) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE passenger_profile SET wallet_balance = ? WHERE user_id = ?")) {
                    stmt.setBigDecimal(1, originalAliceBalance);
                    stmt.setLong(2, SEED_PASSENGER_ALICE_ID);
                    stmt.executeUpdate();
                }
            }
            if (originalSamuelEarnings != null) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE driver_profile SET total_earnings = ?, rating_avg = ?, availability = ?, current_lat = ?, current_lng = ? WHERE user_id = ?")) {
                    stmt.setBigDecimal(1, originalSamuelEarnings);
                    stmt.setBigDecimal(2, originalSamuelRating);
                    stmt.setString(3, originalSamuelAvailability.name());
                    stmt.setDouble(4, originalSamuelLat);
                    stmt.setDouble(5, originalSamuelLng);
                    stmt.setLong(6, SEED_DRIVER_SAMUEL_ID);
                    stmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore seed state after bridge test", e);
        } finally {
            createdTripIds.clear();
            createdVehicleIds.clear();
            createdUserIds.clear();
            createdPricingRuleIds.clear();
            SessionManager.getInstance().clearAllSessions();
        }
    }

    @Test
    @DisplayName("Scenario 1: Mobile driver onboarding -> Desktop Admin approval -> Admin tariff update -> Mobile fare quote sync")
    void testDriverOnboardingAndTariffSync() throws Exception {
        long seed = Math.abs(System.nanoTime()) % 900_000L + 100_000L;
        String tag = Long.toString(seed);
        String driverEmail = "bridge.drv." + tag + "@viame.test";
        String driverPhone = "+2376" + String.format("%08d", (seed + 31) % 100_000_000L);
        String licenceNumber = "CMR-BR-" + tag;
        String plateNumber = "LT " + tag.substring(0, 4) + " BR";

        // 1. Mobile registers a brand-new driver + vehicle
        String registerBody = String.format("""
                {
                    "email": "%s",
                    "phone": "%s",
                    "password": "%s",
                    "fullName": "Bridge Driver %s",
                    "licenceNumber": "%s",
                    "plateNumber": "%s",
                    "make": "Toyota",
                    "model": "Corolla",
                    "seats": 4
                }
                """, driverEmail, driverPhone, SEED_PASSWORD, tag, licenceNumber, plateNumber);

        HttpResponse<String> registerRes = postJson("/api/auth/register/driver", null, registerBody);
        assertEquals(201, registerRes.statusCode(), registerRes.body());
        Map<String, String> registerJson = JsonUtil.parseSimpleJson(registerRes.body());
        assertTrue(Boolean.parseBoolean(registerJson.get("success")));
        assertEquals("PENDING", registerJson.get("verificationStatus"));

        long newDriverId = Long.parseLong(registerJson.get("userId"));
        createdUserIds.add(newDriverId);
        vehicleDAO.findByDriverId(newDriverId).ifPresent(v -> createdVehicleIds.add(v.getId()));

        // 2. Desktop AdminService sees the pending driver and vehicle, then approves both
        List<Driver> pendingDrivers = adminService.getPendingVerificationDrivers();
        assertTrue(pendingDrivers.stream().anyMatch(d ->
                d.getId() == newDriverId && d.getVerificationStatus() == DriverVerificationStatus.PENDING),
                "Desktop AdminService must list newly mobile-registered pending driver");

        Vehicle pendingVehicle = adminService.getVehicleForDriver(newDriverId).orElseThrow();
        assertEquals(VehicleStatus.PENDING, pendingVehicle.getStatus());

        assertTrue(adminService.approveDriver(ADMIN_ID, newDriverId));

        assertFalse(adminService.getPendingVerificationDrivers().stream()
                .anyMatch(d -> d.getId() == newDriverId),
                "Approved driver must no longer appear in pending list");
        Driver approvedDriver = driverDAO.findById(newDriverId).orElseThrow();
        assertEquals(DriverVerificationStatus.APPROVED, approvedDriver.getVerificationStatus());
        Vehicle activeVehicle = adminService.getVehicleForDriver(newDriverId).orElseThrow();
        assertEquals(VehicleStatus.ACTIVE, activeVehicle.getStatus());

        // Approved driver can log in via Mobile REST API
        String newDriverToken = loginViaRest(driverEmail, SEED_PASSWORD).get("token");
        assertNotNull(newDriverToken);

        // 3. Desktop Admin updates tariff; Mobile /api/fare/estimate immediately reflects the new rates
        String estimatePayload = """
                {
                    "pickupLat": 4.051056,
                    "pickupLng": 9.767869,
                    "dropoffLat": 4.075833,
                    "dropoffLng": 9.718611
                }
                """;
        HttpResponse<String> beforeTariffRes = postJson("/api/fare/estimate", null, estimatePayload);
        assertEquals(200, beforeTariffRes.statusCode());
        Map<String, String> beforeQuote = JsonUtil.parseSimpleJson(beforeTariffRes.body());
        BigDecimal initialBaseFare = new BigDecimal(beforeQuote.get("baseFare"));
        BigDecimal initialTotal = new BigDecimal(beforeQuote.get("totalAmount"));
        assertEquals(0, originalActiveRule.getBaseFare().compareTo(initialBaseFare));

        PricingRule promoRule = adminService.updateTariff(
                ADMIN_ID,
                "Bridge Express Tariff",
                new BigDecimal("1000.00"),
                new BigDecimal("350.00"),
                new BigDecimal("45.00"),
                new BigDecimal("1.50"));
        assertTrue(promoRule.getId() > 0);
        assertTrue(promoRule.isActive());
        createdPricingRuleIds.add(promoRule.getId());

        HttpResponse<String> afterTariffRes = postJson("/api/fare/estimate", null, estimatePayload);
        assertEquals(200, afterTariffRes.statusCode());
        Map<String, String> afterQuote = JsonUtil.parseSimpleJson(afterTariffRes.body());

        BigDecimal updatedBaseFare = new BigDecimal(afterQuote.get("baseFare"));
        BigDecimal updatedSurge = new BigDecimal(afterQuote.get("surge"));
        BigDecimal updatedTotal = new BigDecimal(afterQuote.get("totalAmount"));
        BigDecimal distanceKm = new BigDecimal(afterQuote.get("distanceKm"));
        int durationMin = Integer.parseInt(afterQuote.get("durationMin"));

        assertEquals(0, new BigDecimal("1000.00").compareTo(updatedBaseFare));
        assertTrue(updatedSurge.compareTo(new BigDecimal("1.50")) >= 0);
        assertTrue(updatedTotal.compareTo(initialTotal) > 0);

        FareQuote expectedQuote = FareCalculationService.calculate(
                promoRule, distanceKm, durationMin, updatedSurge, "EXPECTED");
        assertEquals(0, expectedQuote.totalAmount().compareTo(updatedTotal));
    }

    @Test
    @DisplayName("Scenario 2: Full cross-module ride lifecycle, WALLET settlement, 5-star rating, and BI revenue/driver sync")
    void testFullCrossModuleRideLifecycleAndRevenueSync() throws Exception {
        // 1. Mobile Passenger logs in and checks wallet balance
        Map<String, String> passLogin = loginViaRest(SEED_PASSENGER_EMAIL, SEED_PASSWORD);
        String passengerToken = passLogin.get("token");
        assertEquals(String.valueOf(SEED_PASSENGER_ALICE_ID), passLogin.get("userId"));
        assertEquals("PASSENGER", passLogin.get("role"));

        HttpResponse<String> walletBeforeRes = getWithAuth(
                "/api/wallet/balance?passengerId=" + SEED_PASSENGER_ALICE_ID, passengerToken);
        assertEquals(200, walletBeforeRes.statusCode());
        BigDecimal reportedBalance = new BigDecimal(JsonUtil.parseSimpleJson(walletBeforeRes.body()).get("balance"));
        assertEquals(0, originalAliceBalance.compareTo(reportedBalance));

        // 2. Mobile Passenger requests a WALLET ride
        String tripRequestBody = String.format("""
                {
                    "passengerId": %d,
                    "pickupLat": 4.051056,
                    "pickupLng": 9.767869,
                    "pickupAddress": "Akwa Palace Hotel, Douala",
                    "dropoffLat": 4.075833,
                    "dropoffLng": 9.718611,
                    "dropoffAddress": "Bonamoussadi Market, Douala",
                    "paymentMethod": "WALLET"
                }
                """, SEED_PASSENGER_ALICE_ID);
        HttpResponse<String> reqRes = postJson("/api/trips/request", passengerToken, tripRequestBody);
        assertEquals(201, reqRes.statusCode(), reqRes.body());
        Map<String, String> reqJson = JsonUtil.parseSimpleJson(reqRes.body());
        assertTrue(Boolean.parseBoolean(reqJson.get("success")));
        assertEquals("REQUESTED", reqJson.get("status"));
        long tripId = Long.parseLong(reqJson.get("tripId"));
        createdTripIds.add(tripId);

        // 3. Desktop AdminService & TripService immediately observe the REQUESTED trip
        List<Trip> liveTripsBeforeAccept = adminService.getLiveFleetTrips();
        assertTrue(liveTripsBeforeAccept.stream().anyMatch(t ->
                t.getId() == tripId && t.getStatus() == TripStatus.REQUESTED));
        List<Trip> desktopPending = tripService.getPendingBroadcastRequests();
        assertTrue(desktopPending.stream().anyMatch(t -> t.getId() == tripId));

        // 4. Mobile Driver logs in, sets ONLINE with optional lat/lng, updates GPS, and polls /api/trips/pending
        Map<String, String> drvLogin = loginViaRest(SEED_DRIVER_EMAIL, SEED_PASSWORD);
        String driverToken = drvLogin.get("token");
        assertEquals(String.valueOf(SEED_DRIVER_SAMUEL_ID), drvLogin.get("userId"));
        assertEquals("DRIVER", drvLogin.get("role"));

        HttpResponse<String> availRes = postJson("/api/drivers/availability", driverToken,
                String.format("{\"driverId\":%d,\"status\":\"ONLINE\",\"lat\":4.052100,\"lng\":9.768100}",
                        SEED_DRIVER_SAMUEL_ID));
        assertEquals(200, availRes.statusCode());
        assertEquals("ONLINE", JsonUtil.parseSimpleJson(availRes.body()).get("status"));

        HttpResponse<String> locRes = postJson("/api/drivers/location", driverToken,
                String.format("{\"driverId\":%d,\"lat\":4.053200,\"lng\":9.768500}", SEED_DRIVER_SAMUEL_ID));
        assertEquals(200, locRes.statusCode());
        Driver relocatedDriver = driverDAO.findById(SEED_DRIVER_SAMUEL_ID).orElseThrow();
        assertEquals(4.053200, relocatedDriver.getCurrentLat(), 0.00001);
        assertEquals(9.768500, relocatedDriver.getCurrentLng(), 0.00001);

        HttpResponse<String> pendingRes = getWithAuth("/api/trips/pending", driverToken);
        assertEquals(200, pendingRes.statusCode());
        Map<String, Object> pendingObj = JsonUtil.parseObject(pendingRes.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pendingList = (List<Map<String, Object>>) pendingObj.get("trips");
        Map<String, Object> matchedPending = pendingList.stream()
                .filter(m -> Long.parseLong(String.valueOf(m.get("tripId"))) == tripId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pending trips did not contain tripId " + tripId));
        assertNotNull(matchedPending.get("totalAmount"));
        assertNotNull(matchedPending.get("estimatedFare"));
        BigDecimal expectedFare = new BigDecimal(String.valueOf(matchedPending.get("totalAmount")));
        assertTrue(expectedFare.compareTo(BigDecimal.ZERO) > 0);
        assertEquals("XAF", matchedPending.get("currency"));

        // 5. Driver accepts via POST /api/trips/accept, then recovers active trip via GET /api/trips/status?driverId=
        HttpResponse<String> acceptRes = postJson("/api/trips/accept", driverToken,
                String.format("{\"tripId\":%d,\"driverId\":%d}", tripId, SEED_DRIVER_SAMUEL_ID));
        assertEquals(200, acceptRes.statusCode(), acceptRes.body());
        Map<String, String> acceptJson = JsonUtil.parseSimpleJson(acceptRes.body());
        assertEquals("ASSIGNED", acceptJson.get("status"));
        assertEquals(String.valueOf(SEED_DRIVER_SAMUEL_ID), acceptJson.get("driverId"));

        HttpResponse<String> driverActiveRes = getWithAuth(
                "/api/trips/status?driverId=" + SEED_DRIVER_SAMUEL_ID, driverToken);
        assertEquals(200, driverActiveRes.statusCode());
        Map<String, String> driverActiveJson = JsonUtil.parseSimpleJson(driverActiveRes.body());
        assertEquals(String.valueOf(tripId), driverActiveJson.get("tripId"));
        assertEquals("ASSIGNED", driverActiveJson.get("status"));

        // 6. Passenger polls GET /api/trips/status?passengerId= and verifies enriched driver & vehicle fields
        HttpResponse<String> passStatusRes = getWithAuth(
                "/api/trips/status?passengerId=" + SEED_PASSENGER_ALICE_ID, passengerToken);
        assertEquals(200, passStatusRes.statusCode());
        Map<String, String> passStatusJson = JsonUtil.parseSimpleJson(passStatusRes.body());
        assertEquals("ASSIGNED", passStatusJson.get("status"));
        assertEquals("Samuel Eto'o", passStatusJson.get("driverName"));
        assertEquals("+237670000002", passStatusJson.get("driverPhone"));
        assertEquals(0, originalSamuelRating.compareTo(new BigDecimal(passStatusJson.get("driverRating"))));
        assertTrue(passStatusJson.get("driverAvatarUrl").startsWith("/api/avatar?seed="));
        assertEquals("Toyota", passStatusJson.get("vehicleMake"));
        assertEquals("Yaris", passStatusJson.get("vehicleModel"));
        assertEquals("LT 452 AB", passStatusJson.get("vehiclePlate"));
        assertEquals(0, expectedFare.compareTo(new BigDecimal(passStatusJson.get("totalAmount"))));

        // Snapshot today's revenue right before completion
        LocalDate today = LocalDate.now();
        Optional<DailyRevenueRow> beforeTodayOpt = reportService.dailyRevenue(today, today).stream()
                .filter(r -> today.equals(r.date()))
                .findFirst();
        int beforeTripsToday = beforeTodayOpt.map(DailyRevenueRow::tripsCompleted).orElse(0);
        BigDecimal beforeGrossToday = beforeTodayOpt.map(DailyRevenueRow::grossRevenue).orElse(BigDecimal.ZERO);

        // 7. Driver advances ride: EN_ROUTE -> ARRIVED -> START -> COMPLETE (WALLET)
        String[] transitions = {"EN_ROUTE", "ARRIVED", "START"};
        String[] expectedStatuses = {"DRIVER_EN_ROUTE", "ARRIVED", "IN_PROGRESS"};
        for (int i = 0; i < transitions.length; i++) {
            HttpResponse<String> stepRes = postJson("/api/trips/status", driverToken,
                    String.format("{\"tripId\":%d,\"driverId\":%d,\"action\":\"%s\"}",
                            tripId, SEED_DRIVER_SAMUEL_ID, transitions[i]));
            assertEquals(200, stepRes.statusCode());
            assertEquals(expectedStatuses[i], JsonUtil.parseSimpleJson(stepRes.body()).get("status"));
        }

        HttpResponse<String> completeRes = postJson("/api/trips/status", driverToken,
                String.format("{\"tripId\":%d,\"driverId\":%d,\"action\":\"COMPLETE\",\"paymentMethod\":\"WALLET\"}",
                        tripId, SEED_DRIVER_SAMUEL_ID));
        assertEquals(200, completeRes.statusCode(), completeRes.body());
        Map<String, String> completeJson = JsonUtil.parseSimpleJson(completeRes.body());
        assertEquals("COMPLETED", completeJson.get("status"));
        assertNotNull(completeJson.get("completedAt"));

        // Verify Desktop AdminService no longer sees it as live & wallet was debited atomically
        assertFalse(adminService.getLiveFleetTrips().stream().anyMatch(t -> t.getId() == tripId));
        HttpResponse<String> walletAfterRes = getWithAuth(
                "/api/wallet/balance?passengerId=" + SEED_PASSENGER_ALICE_ID, passengerToken);
        assertEquals(200, walletAfterRes.statusCode());
        BigDecimal walletAfter = new BigDecimal(JsonUtil.parseSimpleJson(walletAfterRes.body()).get("balance"));
        assertEquals(0, originalAliceBalance.subtract(expectedFare).compareTo(walletAfter));

        Payment settledPayment = paymentService.getPaymentForTrip(tripId).orElseThrow();
        assertEquals(PaymentStatus.PAID, settledPayment.getStatus());
        assertEquals(PaymentMethod.WALLET, settledPayment.getMethod());
        assertEquals(0, expectedFare.compareTo(settledPayment.getAmount()));

        // 8. Mobile Passenger submits 5-star rating via POST /api/ratings
        String ratingBody = String.format("""
                {
                    "tripId": %d,
                    "raterId": %d,
                    "rateeId": %d,
                    "score": 5,
                    "comment": "Smooth mobile-to-desktop bridge ride!"
                }
                """, tripId, SEED_PASSENGER_ALICE_ID, SEED_DRIVER_SAMUEL_ID);
        HttpResponse<String> ratingRes = postJson("/api/ratings", passengerToken, ratingBody);
        assertEquals(201, ratingRes.statusCode(), ratingRes.body());
        Map<String, String> ratingJson = JsonUtil.parseSimpleJson(ratingRes.body());
        assertTrue(Boolean.parseBoolean(ratingJson.get("success")));
        assertEquals("5", ratingJson.get("score"));

        // 9. Desktop ReportService.dailyRevenue(today, today) & DriverDAO.findById confirm 20%/80% split + earnings/rating
        DailyRevenueRow afterToday = reportService.dailyRevenue(today, today).stream()
                .filter(r -> today.equals(r.date()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected daily revenue row for " + today));

        assertEquals(beforeTripsToday + 1, afterToday.tripsCompleted());
        assertEquals(0, beforeGrossToday.add(expectedFare).compareTo(afterToday.grossRevenue()));
        assertEquals(0, ReportMath.commission(afterToday.grossRevenue()).compareTo(afterToday.platformCommission()));
        assertEquals(0, ReportMath.driverPayout(afterToday.grossRevenue()).compareTo(afterToday.driverPayouts()));

        BigDecimal expectedCommission = expectedFare.multiply(PaymentService.PLATFORM_COMMISSION_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedDriverNet = expectedFare.subtract(expectedCommission);

        Driver updatedSamuel = driverDAO.findById(SEED_DRIVER_SAMUEL_ID).orElseThrow();
        assertEquals(0, originalSamuelEarnings.add(expectedDriverNet).compareTo(updatedSamuel.getTotalEarnings()));
        assertEquals(DriverAvailability.ONLINE, updatedSamuel.getAvailability());

        // Trigger trg_after_rating_insert recalculates driver_profile.rating_avg from rating rows
        BigDecimal expectedRecalculatedAvg = queryRecalculatedDriverRating(SEED_DRIVER_SAMUEL_ID);
        assertEquals(0, expectedRecalculatedAvg.compareTo(updatedSamuel.getRatingAvg()));
        assertTrue(updatedSamuel.getRatingAvg().compareTo(originalSamuelRating) > 0,
                "5-star rating should increase Samuel's rating average above " + originalSamuelRating);
    }

    private Map<String, String> loginViaRest(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        HttpResponse<String> res = postJson("/api/auth/login", null, body);
        assertEquals(200, res.statusCode(), "REST login failed for " + email + ": " + res.body());
        Map<String, String> json = JsonUtil.parseSimpleJson(res.body());
        issuedTokens.add(json.get("token"));
        return json;
    }

    private HttpResponse<String> postJson(String path, String token, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithAuth(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void snapshotSeedState() throws Exception {
        originalActiveRule = adminService.getActivePricingRule();

        Passenger alice = passengerDAO.findById(SEED_PASSENGER_ALICE_ID).orElseThrow();
        originalAliceBalance = alice.getWalletBalance();

        Driver samuel = driverDAO.findById(SEED_DRIVER_SAMUEL_ID).orElseThrow();
        originalSamuelEarnings = samuel.getTotalEarnings();
        originalSamuelRating = samuel.getRatingAvg();
        originalSamuelAvailability = samuel.getAvailability();
        originalSamuelLat = samuel.getCurrentLat();
        originalSamuelLng = samuel.getCurrentLng();

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            baselineMaxAuditLogId = queryMaxId(conn, "audit_log");
            baselineMaxPricingRuleId = queryMaxId(conn, "pricing_rule");
        }
    }

    private static BigDecimal queryRecalculatedDriverRating(long driverId) throws Exception {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT ROUND(AVG(score), 2) FROM rating WHERE ratee_id = ?")) {
            stmt.setLong(1, driverId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
            }
        }
    }

    private static long queryMaxId(Connection conn, String table) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM " + table);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static void execUpdate(Connection conn, String sql, long param) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, param);
            stmt.executeUpdate();
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
}

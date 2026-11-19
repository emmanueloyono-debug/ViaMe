package com.viame.api;

import com.viame.dao.DBConnection;
import com.viame.dao.DriverDAO;
import com.viame.dao.SessionDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.SessionDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserStatus;
import com.viame.model.enums.VehicleStatus;
import com.viame.service.AuthenticationService;
import com.viame.service.DriverMatchingService;
import com.viame.service.FareCalculationService;
import com.viame.service.PaymentService;
import com.viame.service.RatingService;
import com.viame.service.ReportService;
import com.viame.service.TripService;
import com.viame.util.JsonUtil;
import com.viame.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("REST API Mobile Enrichment, Driver GPS Location & Active Trip Lookup Tests")
class RestApiMobileEnrichmentTest {

    private static final int PORT = 18083;
    private RestApiServer server;
    private HttpClient client;

    private SessionDAO sessionDAO;
    private UserDAO userDAO;
    private DriverDAO driverDAO;
    private VehicleDAO vehicleDAO;
    private TripService tripService;

    private String passengerToken;
    private String driverToken;
    private String adminToken;

    private Passenger testPassenger;
    private Driver testDriver;
    private Vehicle testVehicle;
    private String testPassengerEmail;
    private String testDriverEmail;
    private final List<Long> createdTripIds = new ArrayList<>();
    private final List<Long> extraUserIds = new ArrayList<>();
    private final List<Long> extraVehicleIds = new ArrayList<>();
    private final List<String> sessionTokens = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sessionDAO = new SessionDAOImpl();
        userDAO = new UserDAOImpl();
        driverDAO = new DriverDAOImpl();
        vehicleDAO = new VehicleDAOImpl();
        tripService = new TripService();
        client = HttpClient.newHttpClient();

        server = new RestApiServer(PORT);
        server.start();

        String tag = Long.toString(Math.abs(System.nanoTime()) % 900_000L + 100_000L);
        testPassengerEmail = "mob.pass." + tag + "@viame.test";
        testDriverEmail = "mob.drv." + tag + "@viame.test";

        AuthenticationService auth = new AuthenticationService();
        testPassenger = auth.registerPassenger(testPassengerEmail, "+2376" + String.format("%08d", Long.parseLong(tag)),
                "Password123!", "Mobile Passenger " + tag, PaymentMethod.CASH);

        testDriver = auth.registerDriver(testDriverEmail, "+2376" + String.format("%08d", (Long.parseLong(tag) + 1) % 100000000),
                "Password123!", "Mobile Driver " + tag, "LIC-MOB-" + tag,
                "LT " + tag.substring(0, 3) + " MB", "Toyota", "Corolla", 4);

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            driverDAO.updateVerificationStatus(conn, testDriver.getId(), DriverVerificationStatus.APPROVED);
            vehicleDAO.updateStatusByDriver(conn, testDriver.getId(), VehicleStatus.ACTIVE);
        }
        driverDAO.updateAvailability(testDriver.getId(), DriverAvailability.ONLINE);
        testVehicle = vehicleDAO.findByDriverId(testDriver.getId()).orElseThrow();

        passengerToken = loginAndGetToken(testPassengerEmail, "Password123!");
        driverToken = loginAndGetToken(testDriverEmail, "Password123!");
        adminToken = loginAndGetToken("admin@viame.cm", "Password123!");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        for (String tok : sessionTokens) {
            try {
                sessionDAO.deleteByTokenHash(RestApiServer.hashToken(tok));
            } catch (Exception ignored) {
            }
        }
        sessionTokens.clear();

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            for (Long tripId : createdTripIds) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payment WHERE ride_id = ?")) {
                    stmt.setLong(1, tripId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM fare WHERE ride_id = ?")) {
                    stmt.setLong(1, tripId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ride WHERE id = ?")) {
                    stmt.setLong(1, tripId);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
            if (testDriver != null) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM audit_log WHERE user_id = ?")) {
                    stmt.setLong(1, testDriver.getId());
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
            if (testPassenger != null) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM audit_log WHERE user_id = ?")) {
                    stmt.setLong(1, testPassenger.getId());
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
            for (Long uid : extraUserIds) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM audit_log WHERE user_id = ?")) {
                    stmt.setLong(1, uid);
                    stmt.executeUpdate();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        createdTripIds.clear();

        for (Long vid : extraVehicleIds) {
            try {
                vehicleDAO.delete(vid);
            } catch (Exception ignored) {
            }
        }
        extraVehicleIds.clear();
        for (Long uid : extraUserIds) {
            try {
                userDAO.delete(uid);
            } catch (Exception ignored) {
            }
        }
        extraUserIds.clear();

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
        if (testPassenger != null && testPassenger.getId() > 0) {
            try {
                userDAO.delete(testPassenger.getId());
            } catch (Exception ignored) {
            }
        }
        SessionManager.getInstance().clearAllSessions();
    }

    private String registerSecondaryDriverAndGetToken() throws Exception {
        long seed = Math.abs(System.nanoTime()) % 900_000L + 100_000L;
        String tag = Long.toString(seed);
        String email = "mob.drv2." + tag + "@viame.test";
        String phone = "+2376" + String.format("%08d", (seed + 77) % 100_000_000L);
        String plate = "LT " + tag.substring(0, 4) + " DR";

        AuthenticationService auth = new AuthenticationService();
        Driver second = auth.registerDriver(email, phone, "Password123!",
                "Secondary Driver " + tag, "LIC-SEC-" + tag, plate, "Honda", "Civic", 4);
        extraUserIds.add(second.getId());
        vehicleDAO.findByDriverId(second.getId()).ifPresent(v -> extraVehicleIds.add(v.getId()));
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            driverDAO.updateVerificationStatus(conn, second.getId(), DriverVerificationStatus.APPROVED);
            vehicleDAO.updateStatusByDriver(conn, second.getId(), VehicleStatus.ACTIVE);
        }
        return loginAndGetToken(email, "Password123!");
    }

    private HttpResponse<String> postJson(String path, String token, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithAuth(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        String token = JsonUtil.parseSimpleJson(res.body()).get("token");
        sessionTokens.add(token);
        return token;
    }

    @Test
    @DisplayName("POST /api/drivers/location enforces auth, anti-spoofing, validation, Locale.US formatting and updates DB")
    void testDriverLocationEndpoint() throws Exception {
        // 1. Unauthenticated -> 401
        HttpResponse<String> unauth = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/location"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":" + testDriver.getId() + ",\"lat\":4.06,\"lng\":9.74}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauth.statusCode());

        // 2. Spoofing by passenger -> 403
        HttpResponse<String> spoof = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/location"))
                .header("Authorization", "Bearer " + passengerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":" + testDriver.getId() + ",\"lat\":4.06,\"lng\":9.74}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoof.statusCode());

        // 3. Invalid coordinates -> 400
        HttpResponse<String> badCoords = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/location"))
                .header("Authorization", "Bearer " + driverToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":" + testDriver.getId() + ",\"lat\":95.0,\"lng\":200.0}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, badCoords.statusCode());

        // 4. Unknown driver by Admin -> 404
        HttpResponse<String> missing = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/location"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":999999,\"lat\":4.06,\"lng\":9.74}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missing.statusCode());

        // 5. Valid update under non-US Locale -> 200 with dot decimals
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            HttpResponse<String> ok = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + "/api/drivers/location"))
                    .header("Authorization", "Bearer " + driverToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":" + testDriver.getId() + ",\"lat\":4.061234,\"lng\":9.745678}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ok.statusCode());
            assertTrue(ok.body().contains("\"lat\":4.061234"));
            assertTrue(ok.body().contains("\"lng\":9.745678"));
        } finally {
            Locale.setDefault(original);
        }

        Driver reloaded = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(4.061234, reloaded.getCurrentLat(), 0.00001);
        assertEquals(9.745678, reloaded.getCurrentLng(), 0.00001);
    }

    @Test
    @DisplayName("POST /api/drivers/availability supports optional lat/lng updates and validates coordinates")
    void testDriverAvailabilityWithOptionalCoordinates() throws Exception {
        // Invalid coordinates reject with 400 and do not alter availability
        HttpResponse<String> badCoords = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability"))
                .header("Authorization", "Bearer " + driverToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\",\"lat\":-99.0,\"lng\":9.75}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, badCoords.statusCode());
        assertEquals(DriverAvailability.ONLINE, driverDAO.findById(testDriver.getId()).orElseThrow().getAvailability());

        // Valid coordinates + OFFLINE updates both
        HttpResponse<String> valid = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability"))
                .header("Authorization", "Bearer " + driverToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\",\"lat\":4.072500,\"lng\":9.731500}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, valid.statusCode());

        Driver reloaded = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(DriverAvailability.OFFLINE, reloaded.getAvailability());
        assertEquals(4.072500, reloaded.getCurrentLat(), 0.00001);
        assertEquals(9.731500, reloaded.getCurrentLng(), 0.00001);
    }

    @Test
    @DisplayName("Enriched trip JSON & summary include fare, driver, and vehicle metadata, and support ?driverId=")
    void testEnrichedTripAndDriverStatusLookup() throws Exception {
        // 1. Driver with no active trip -> 404
        HttpResponse<String> noTripRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/status?driverId=" + testDriver.getId()))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noTripRes.statusCode());

        // 2. Spoofing another driver's active trip -> 403
        HttpResponse<String> spoofRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/status?driverId=" + testDriver.getId()))
                .header("Authorization", "Bearer " + passengerToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoofRes.statusCode());

        // 3. Create a REQUESTED trip and verify enriched /api/trips/pending
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi", PaymentMethod.CASH);
        createdTripIds.add(trip.getId());

        HttpResponse<String> pendingRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/pending"))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pendingRes.statusCode());
        assertTrue(pendingRes.body().contains("\"totalAmount\":"));
        assertTrue(pendingRes.body().contains("\"estimatedFare\":"));
        assertTrue(pendingRes.body().contains("\"surgeMultiplier\":"));
        assertTrue(pendingRes.body().contains("\"currency\":\"XAF\""));

        // 4. Driver accepts trip, then query GET /api/trips/status?driverId=...
        tripService.acceptTrip(trip.getId(), testDriver.getId());

        HttpResponse<String> activeRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/status?driverId=" + testDriver.getId()))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, activeRes.statusCode());
        Map<String, String> json = JsonUtil.parseSimpleJson(activeRes.body());
        assertEquals(String.valueOf(trip.getId()), json.get("tripId"));
        assertEquals("ASSIGNED", json.get("status"));

        // Fare fields
        assertNotNull(json.get("totalAmount"));
        assertEquals(json.get("totalAmount"), json.get("estimatedFare"));
        assertNotNull(json.get("baseFare"));
        assertNotNull(json.get("distanceFare"));
        assertNotNull(json.get("timeFare"));
        assertNotNull(json.get("surgeMultiplier"));
        assertEquals("XAF", json.get("currency"));

        // Driver fields
        assertEquals(testDriver.getFullName(), json.get("driverName"));
        assertEquals(testDriver.getPhone(), json.get("driverPhone"));
        assertEquals(testDriver.getLicenceNumber(), json.get("driverLicence"));
        assertNotNull(json.get("driverRating"));
        assertTrue(json.get("driverAvatarUrl").startsWith("/api/avatar?seed="));

        // Vehicle fields
        assertEquals("Toyota", json.get("vehicleMake"));
        assertEquals("Corolla", json.get("vehicleModel"));
        assertEquals(testVehicle.getPlateNumber(), json.get("vehiclePlate"));
        assertEquals("4", json.get("vehicleSeats"));

        // 5. Cancel trip -> terminal status -> GET ?driverId= returns 404
        tripService.cancelTrip(trip.getId(), testPassenger.getId(), true);
        HttpResponse<String> afterCancelRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/status?driverId=" + testDriver.getId()))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, afterCancelRes.statusCode());
    }

    @Test
    @DisplayName("12-arg legacy RestApiServer constructor remains backward-compatible")
    void testLegacy12ArgConstructorStillWorks() throws Exception {
        RestApiServer legacyServer = new RestApiServer(
                18084,
                new AuthenticationService(),
                new TripService(),
                new FareCalculationService(),
                new PaymentService(),
                new DriverMatchingService(),
                new ReportService(),
                new RatingService(),
                new DriverDAOImpl(),
                new PassengerDAOImpl(),
                new UserDAOImpl(),
                new SessionDAOImpl());
        legacyServer.start();
        try {
            HttpResponse<String> health = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:18084/api/health"))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
        } finally {
            legacyServer.stop();
        }
    }

    @Test
    @DisplayName("POST /api/drivers/location handles same-role spoofing, missing/invalid coords, 405, and admin override")
    void testDriverLocationValidationAndAntiSpoofingEdgeCases() throws Exception {
        String otherDriverToken = registerSecondaryDriverAndGetToken();

        // Same-role anti-spoofing: another DRIVER cannot update testDriver's location -> 403
        HttpResponse<String> driverSpoof = postJson("/api/drivers/location", otherDriverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":4.06,\"lng\":9.74}");
        assertEquals(403, driverSpoof.statusCode());

        // Missing parameters -> 400
        assertEquals(400, postJson("/api/drivers/location", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":4.06}").statusCode());
        assertEquals(400, postJson("/api/drivers/location", driverToken,
                "{\"lat\":4.06,\"lng\":9.74}").statusCode());

        // Boundary / NaN / non-numeric invalid coordinates -> 400
        assertEquals(400, postJson("/api/drivers/location", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":-90.01,\"lng\":9.74}").statusCode());
        assertEquals(400, postJson("/api/drivers/location", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":4.06,\"lng\":-180.01}").statusCode());
        assertEquals(400, postJson("/api/drivers/location", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":\"NaN\",\"lng\":9.74}").statusCode());
        assertEquals(400, postJson("/api/drivers/location", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":\"not-a-number\",\"lng\":9.74}").statusCode());

        // Unsupported HTTP method -> 405
        HttpResponse<String> getRes = getWithAuth("/api/drivers/location", driverToken);
        assertEquals(405, getRes.statusCode());

        // Admin override -> 200 and updates DB
        HttpResponse<String> adminUpdate = postJson("/api/drivers/location", adminToken,
                "{\"driverId\":" + testDriver.getId() + ",\"lat\":4.088888,\"lng\":9.722222}");
        assertEquals(200, adminUpdate.statusCode());
        Driver reloaded = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(4.088888, reloaded.getCurrentLat(), 0.00001);
        assertEquals(9.722222, reloaded.getCurrentLng(), 0.00001);
    }

    @Test
    @DisplayName("POST/GET /api/drivers/availability handles invalid lng/status, spoofing, 404, and non-US Locale coords")
    void testDriverAvailabilityEdgeCases() throws Exception {
        String otherDriverToken = registerSecondaryDriverAndGetToken();

        // Invalid longitude and non-numeric coordinates -> 400 and availability unchanged
        assertEquals(400, postJson("/api/drivers/availability", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\",\"lat\":4.05,\"lng\":181.5}").statusCode());
        assertEquals(400, postJson("/api/drivers/availability", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\",\"lat\":\"NaN\",\"lng\":9.75}").statusCode());
        assertEquals(400, postJson("/api/drivers/availability", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\",\"lat\":\"bad\",\"lng\":9.75}").statusCode());
        assertEquals(DriverAvailability.ONLINE, driverDAO.findById(testDriver.getId()).orElseThrow().getAvailability());

        // Missing or invalid status -> 400
        assertEquals(400, postJson("/api/drivers/availability", driverToken,
                "{\"driverId\":" + testDriver.getId() + "}").statusCode());
        assertEquals(400, postJson("/api/drivers/availability", driverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"status\":\"FLYING\"}").statusCode());

        // Same-role anti-spoofing on POST and GET -> 403
        assertEquals(403, postJson("/api/drivers/availability", otherDriverToken,
                "{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\"}").statusCode());
        assertEquals(403, getWithAuth("/api/drivers/availability?driverId=" + testDriver.getId(), otherDriverToken).statusCode());

        // Nonexistent driver by Admin -> 404
        assertEquals(404, postJson("/api/drivers/availability", adminToken,
                "{\"driverId\":999999,\"status\":\"OFFLINE\"}").statusCode());
        assertEquals(404, getWithAuth("/api/drivers/availability?driverId=999999", adminToken).statusCode());

        // Valid update with coordinates under Locale.FRANCE -> 200 and DB updated
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            HttpResponse<String> ok = postJson("/api/drivers/availability", driverToken,
                    "{\"driverId\":" + testDriver.getId() + ",\"status\":\"OFFLINE\",\"lat\":4.091234,\"lng\":9.711234}");
            assertEquals(200, ok.statusCode());
        } finally {
            Locale.setDefault(original);
        }
        Driver reloaded = driverDAO.findById(testDriver.getId()).orElseThrow();
        assertEquals(DriverAvailability.OFFLINE, reloaded.getAvailability());
        assertEquals(4.091234, reloaded.getCurrentLat(), 0.00001);
        assertEquals(9.711234, reloaded.getCurrentLng(), 0.00001);
    }

    @Test
    @DisplayName("Trip status endpoints cover same-role spoofing, /api/trips/get alias, status transitions, and COMPLETED 404")
    void testTripStatusLifecycleAndEdgeCases() throws Exception {
        String otherDriverToken = registerSecondaryDriverAndGetToken();

        // Passenger with no active trip -> 404; missing params on /api/trips/get -> 400; unknown tripId -> 404
        assertEquals(404, getWithAuth("/api/trips/status?passengerId=" + testPassenger.getId(), passengerToken).statusCode());
        assertEquals(400, getWithAuth("/api/trips/get", passengerToken).statusCode());
        assertEquals(404, getWithAuth("/api/trips/status?tripId=999999", adminToken).statusCode());

        // Create REQUESTED trip and verify passenger lookup before assignment (fare present, no driver/vehicle yet)
        Trip trip = tripService.requestTrip(testPassenger.getId(), 4.051056, 9.767869, "Akwa Palace",
                4.075833, 9.718611, "Bonamoussadi", PaymentMethod.CASH);
        createdTripIds.add(trip.getId());

        HttpResponse<String> requestedRes = getWithAuth("/api/trips/status?passengerId=" + testPassenger.getId(), passengerToken);
        assertEquals(200, requestedRes.statusCode());
        Map<String, String> reqJson = JsonUtil.parseSimpleJson(requestedRes.body());
        assertEquals("REQUESTED", reqJson.get("status"));
        assertNotNull(reqJson.get("totalAmount"));
        assertNull(reqJson.get("driverName"));
        assertNull(reqJson.get("vehiclePlate"));

        // Accept trip
        tripService.acceptTrip(trip.getId(), testDriver.getId());

        // Same-role driver spoofing on ?driverId= -> 403; unrelated driver on ?tripId= -> 403
        assertEquals(403, getWithAuth("/api/trips/status?driverId=" + testDriver.getId(), otherDriverToken).statusCode());
        assertEquals(403, getWithAuth("/api/trips/status?tripId=" + trip.getId(), otherDriverToken).statusCode());

        // Admin lookup by ?driverId= -> 200; /api/trips/get?driverId= alias -> 200; passenger lookup -> 200 enriched
        assertEquals(200, getWithAuth("/api/trips/status?driverId=" + testDriver.getId(), adminToken).statusCode());
        HttpResponse<String> aliasRes = getWithAuth("/api/trips/get?driverId=" + testDriver.getId(), driverToken);
        assertEquals(200, aliasRes.statusCode());
        assertEquals(testDriver.getFullName(), JsonUtil.parseSimpleJson(aliasRes.body()).get("driverName"));

        HttpResponse<String> passAssigned = getWithAuth("/api/trips/status?passengerId=" + testPassenger.getId(), passengerToken);
        assertEquals(200, passAssigned.statusCode());
        Map<String, String> assignedJson = JsonUtil.parseSimpleJson(passAssigned.body());
        assertEquals(testDriver.getFullName(), assignedJson.get("driverName"));
        assertEquals(testVehicle.getPlateNumber(), assignedJson.get("vehiclePlate"));

        // POST /api/trips/status security & validation checks
        assertEquals(403, postJson("/api/trips/status", passengerToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"EN_ROUTE\"}").statusCode());
        assertEquals(403, postJson("/api/trips/status", otherDriverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"EN_ROUTE\"}").statusCode());
        assertEquals(400, postJson("/api/trips/status", driverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"   \"}").statusCode());
        assertEquals(400, postJson("/api/trips/status", driverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"TELEPORT\"}").statusCode());

        // Advance through EN_ROUTE -> ARRIVED -> START -> COMPLETE
        assertEquals("DRIVER_EN_ROUTE", JsonUtil.parseSimpleJson(postJson("/api/trips/status", driverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"EN_ROUTE\"}").body()).get("status"));
        assertEquals("ARRIVED", JsonUtil.parseSimpleJson(postJson("/api/trips/status", driverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"ARRIVED\"}").body()).get("status"));
        assertEquals("IN_PROGRESS", JsonUtil.parseSimpleJson(postJson("/api/trips/status", driverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"START\"}").body()).get("status"));

        HttpResponse<String> completedRes = postJson("/api/trips/status", driverToken,
                "{\"tripId\":" + trip.getId() + ",\"driverId\":" + testDriver.getId() + ",\"action\":\"COMPLETE\",\"paymentMethod\":\"CASH\"}");
        assertEquals(200, completedRes.statusCode());
        Map<String, String> completedJson = JsonUtil.parseSimpleJson(completedRes.body());
        assertEquals("COMPLETED", completedJson.get("status"));
        assertNotNull(completedJson.get("completedAt"));

        // Terminal COMPLETED trip: ?driverId= and ?passengerId= return 404, ?tripId= still returns 200
        assertEquals(404, getWithAuth("/api/trips/status?driverId=" + testDriver.getId(), driverToken).statusCode());
        assertEquals(404, getWithAuth("/api/trips/status?passengerId=" + testPassenger.getId(), passengerToken).statusCode());
        assertEquals(200, getWithAuth("/api/trips/status?tripId=" + trip.getId(), passengerToken).statusCode());
    }

    @Test
    @DisplayName("Login and /api/drivers/available return mobile avatarUrl and locale-independent decimals")
    void testLoginAndAvailableDriversMobileEnrichments() throws Exception {
        // Verify driver login payload enrichment fields (mood=cool)
        HttpResponse<String> drvLoginRes = postJson("/api/auth/login", null,
                "{\"email\":\"" + testDriverEmail + "\",\"password\":\"Password123!\"}");
        assertEquals(200, drvLoginRes.statusCode());
        Map<String, String> drvJson = JsonUtil.parseSimpleJson(drvLoginRes.body());
        String drvTok = drvJson.get("token");
        sessionTokens.add(drvTok);
        assertEquals(drvTok, drvJson.get("sessionToken"));
        assertEquals("ACTIVE", drvJson.get("status"));
        assertNotNull(drvJson.get("expiresAt"));
        assertTrue(drvJson.get("avatarUrl").startsWith("/api/avatar?seed="));
        assertTrue(drvJson.get("avatarUrl").contains("mood=cool"));

        // Verify passenger login payload enrichment fields (mood=happy)
        HttpResponse<String> passLoginRes = postJson("/api/auth/login", null,
                "{\"email\":\"" + testPassengerEmail + "\",\"password\":\"Password123!\"}");
        assertEquals(200, passLoginRes.statusCode());
        Map<String, String> passJson = JsonUtil.parseSimpleJson(passLoginRes.body());
        sessionTokens.add(passJson.get("token"));
        assertTrue(passJson.get("avatarUrl").contains("mood=happy"));

        // Verify /api/drivers/available under French locale
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            HttpResponse<String> availRes = getWithAuth("/api/drivers/available?lat=4.051056&lng=9.767869&radius=5.0", null);
            assertEquals(200, availRes.statusCode());
            assertTrue(availRes.body().contains("\"avatarUrl\":\"/api/avatar?seed="));
            assertTrue(availRes.body().matches(".*\"distanceKm\":\\d+\\.\\d{2}.*"));
            assertTrue(availRes.body().matches(".*\"rating\":\\d+\\.\\d{2}.*"));
        } finally {
            Locale.setDefault(original);
        }
    }
}

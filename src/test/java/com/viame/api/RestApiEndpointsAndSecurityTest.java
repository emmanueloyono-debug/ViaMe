package com.viame.api;

import com.viame.dao.DBConnection;
import com.viame.dao.SessionDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.SessionDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.model.Session;
import com.viame.util.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("REST API Endpoints, Security Verification and Role Authorization Tests")
class RestApiEndpointsAndSecurityTest {

    private RestApiServer server;
    private static final int PORT = 18082;
    private HttpClient client;
    private SessionDAO sessionDAO;
    private UserDAO userDAO;
    private VehicleDAO vehicleDAO;

    private String passengerToken;
    private String driverToken;
    private String adminToken;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdVehicleIds = new ArrayList<>();
    private final List<String> sessionTokens = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sessionDAO = new SessionDAOImpl();
        userDAO = new UserDAOImpl();
        vehicleDAO = new VehicleDAOImpl();
        client = HttpClient.newHttpClient();

        server = new RestApiServer(PORT);
        server.start();

        // Login Alice (Passenger, ID 4)
        passengerToken = loginAndGetToken("passenger.alice@viame.cm", "Password123!");
        // Login Samuel (Driver, ID 2)
        driverToken = loginAndGetToken("driver.samuel@viame.cm", "Password123!");
        // Login Admin (Admin, ID 1)
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
            for (Long vid : createdVehicleIds) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM vehicle WHERE id = ?")) {
                    stmt.setLong(1, vid);
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

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "Login failed for " + email);
        Map<String, String> json = JsonUtil.parseSimpleJson(res.body());
        String token = json.get("token");
        sessionTokens.add(token);
        return token;
    }

    @Test
    @DisplayName("Public passenger and driver registration endpoints validate payloads and create accounts")
    void testRegistrationEndpoints() throws Exception {
        String tag = Long.toString(System.nanoTime() % 1_000_000);

        // 1. Register Passenger
        String passBody = String.format("""
                {
                    "email": "api.pass.%s@viame.test",
                    "phone": "6%s",
                    "password": "Password123!",
                    "fullName": "API Passenger %s",
                    "paymentMethod": "WALLET"
                }
                """, tag, String.format("%08d", Long.parseLong(tag)), tag);

        HttpResponse<String> passRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/register/passenger"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(passBody))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, passRes.statusCode());
        Map<String, String> passJson = JsonUtil.parseSimpleJson(passRes.body());
        assertTrue(Boolean.parseBoolean(passJson.get("success")));
        long newPassId = Long.parseLong(passJson.get("userId"));
        createdUserIds.add(newPassId);

        // 2. Register Driver
        String drvBody = String.format("""
                {
                    "email": "api.drv.%s@viame.test",
                    "phone": "6%s",
                    "password": "Password123!",
                    "fullName": "API Driver %s",
                    "licenceNumber": "CMR-LT-2026-%s",
                    "plateNumber": "LT %s AR",
                    "make": "Toyota",
                    "model": "Yaris",
                    "seats": 4
                }
                """, tag, String.format("%08d", (Long.parseLong(tag) + 1) % 100000000), tag, tag.substring(0, 4), tag.substring(0, 3));

        HttpResponse<String> drvRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/register/driver"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(drvBody))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, drvRes.statusCode());
        Map<String, String> drvJson = JsonUtil.parseSimpleJson(drvRes.body());
        assertTrue(Boolean.parseBoolean(drvJson.get("success")));
        long newDrvId = Long.parseLong(drvJson.get("userId"));
        createdUserIds.add(newDrvId);
        vehicleDAO.findByDriverId(newDrvId).ifPresent(v -> createdVehicleIds.add(v.getId()));

        // 3. Bad registration payload (invalid email)
        HttpResponse<String> badRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/register/passenger"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"not-an-email\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, badRes.statusCode());
    }

    @Test
    @DisplayName("Driver availability endpoints enforce auth, anti-spoofing and update status")
    void testDriverAvailabilityEndpoints() throws Exception {
        // Unauthenticated POST returns 401
        HttpResponse<String> unauthPost = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":2,\"status\":\"OFFLINE\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthPost.statusCode());

        // Passenger trying to update driver availability returns 403 (ID spoofing / role check)
        HttpResponse<String> spoofPost = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability"))
                .header("Authorization", "Bearer " + passengerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":2,\"status\":\"OFFLINE\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoofPost.statusCode());

        // Driver updating their own availability returns 200
        HttpResponse<String> validPost = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability"))
                .header("Authorization", "Bearer " + driverToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":2,\"status\":\"OFFLINE\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validPost.statusCode());
        assertTrue(validPost.body().contains("\"status\":\"OFFLINE\""));

        // GET availability for driver
        HttpResponse<String> getRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability?driverId=2"))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getRes.statusCode());
        assertTrue(getRes.body().contains("\"status\":\"OFFLINE\""));

        // Reset driver back ONLINE
        client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/availability"))
                .header("Authorization", "Bearer " + driverToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"driverId\":2,\"status\":\"ONLINE\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Driver earnings endpoint enforces auth and anti-spoofing")
    void testDriverEarningsEndpoint() throws Exception {
        // Missing driverId query parameter -> 400
        HttpResponse<String> noParam = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/earnings"))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, noParam.statusCode());

        // Passenger trying to view driver earnings -> 403
        HttpResponse<String> spoof = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/earnings?driverId=2"))
                .header("Authorization", "Bearer " + passengerToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoof.statusCode());

        // Driver viewing their own earnings -> 200
        HttpResponse<String> valid = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/drivers/earnings?driverId=2"))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, valid.statusCode());
        assertTrue(valid.body().contains("\"netEarnings\""));
        assertTrue(valid.body().contains("\"driverId\":2"));
    }

    @Test
    @DisplayName("Rating endpoint validates score, authentication and anti-spoofing")
    void testRatingEndpoints() throws Exception {
        // GET ratings without userId -> 400
        HttpResponse<String> noUser = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/ratings"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, noUser.statusCode());

        // GET ratings with userId 2 -> 200
        HttpResponse<String> getRatings = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/ratings?userId=2"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getRatings.statusCode());
        assertTrue(getRatings.body().contains("\"ratings\""));

        // POST rating unauthenticated -> 401
        HttpResponse<String> unauthPost = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/ratings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"tripId\":1,\"raterId\":4,\"rateeId\":2,\"score\":5}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthPost.statusCode());

        // POST rating spoofing raterId (driver 2 claims to be rater 4) -> 403
        HttpResponse<String> spoofPost = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/ratings"))
                .header("Authorization", "Bearer " + driverToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"tripId\":1,\"raterId\":4,\"rateeId\":2,\"score\":5}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoofPost.statusCode());
    }

    @Test
    @DisplayName("Wallet topup endpoint validates passenger identity and minimum deposit")
    void testWalletTopupEndpoint() throws Exception {
        // Below minimum amount -> 400
        HttpResponse<String> belowMin = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/topup"))
                .header("Authorization", "Bearer " + passengerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"passengerId\":4,\"amount\":\"100.00\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, belowMin.statusCode());
        assertTrue(belowMin.body().contains("Minimum wallet top-up"));

        // Valid topup (+1000 XAF)
        HttpResponse<String> valid = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/topup"))
                .header("Authorization", "Bearer " + passengerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"passengerId\":4,\"amount\":\"1000.00\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, valid.statusCode());
        assertTrue(valid.body().contains("\"success\":true"));
        assertTrue(valid.body().contains("\"newBalance\""));
    }

    @Test
    @DisplayName("Pending broadcast trips endpoint enforces DRIVER role restriction")
    void testPendingTripsEndpoint() throws Exception {
        // Passenger request -> 403
        HttpResponse<String> passRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/pending"))
                .header("Authorization", "Bearer " + passengerToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, passRes.statusCode());

        // Driver request -> 200
        HttpResponse<String> drvRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/pending"))
                .header("Authorization", "Bearer " + driverToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, drvRes.statusCode());
        assertTrue(drvRes.body().contains("\"trips\""));
    }

    @Test
    @DisplayName("All BI report endpoints enforce ADMIN role and return valid formatted JSON")
    void testAllReportEndpoints() throws Exception {
        String[] reportEndpoints = {
                "/api/reports/revenue",
                "/api/reports/drivers",
                "/api/reports/peak-hours",
                "/api/reports/pickup-zones",
                "/api/reports/cancellations",
                "/api/reports/loyalty"
        };

        for (String ep : reportEndpoints) {
            // Passenger attempt -> 403 Forbidden
            HttpResponse<String> passRes = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + ep))
                    .header("Authorization", "Bearer " + passengerToken)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, passRes.statusCode(), "Endpoint " + ep + " should reject non-admin");

            // Driver attempt -> 403 Forbidden
            HttpResponse<String> drvRes = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + ep))
                    .header("Authorization", "Bearer " + driverToken)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, drvRes.statusCode(), "Endpoint " + ep + " should reject driver");

            // Admin attempt -> 200 OK
            HttpResponse<String> adminRes = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + ep + "?from=2026-10-01&to=2026-11-01"))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, adminRes.statusCode(), "Endpoint " + ep + " should succeed for admin");
            assertTrue(adminRes.body().contains("\"rows\""), "Report body must contain 'rows'");
        }
    }

    @Test
    @DisplayName("Expired session token returns 401 Unauthorized")
    void testExpiredSessionToken() throws Exception {
        String expiredToken = "expired_raw_token_" + System.currentTimeMillis();
        String tokenHash = RestApiServer.hashToken(expiredToken);
        Instant pastExpiry = Instant.now().minus(2, ChronoUnit.HOURS);

        Session expiredSession = new Session(tokenHash, 4L, pastExpiry, "127.0.0.1");
        sessionDAO.save(expiredSession);

        try {
            HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=4"))
                    .header("Authorization", "Bearer " + expiredToken)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, res.statusCode());
            assertTrue(res.body().contains("Session token has expired"));
        } finally {
            sessionDAO.deleteByTokenHash(tokenHash);
        }
    }
}

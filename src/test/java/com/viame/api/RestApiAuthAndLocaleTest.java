package com.viame.api;

import com.viame.dao.SessionDAO;
import com.viame.dao.impl.SessionDAOImpl;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("REST API Session Token Auth, Authorization and Locale Formatting Tests")
class RestApiAuthAndLocaleTest {

    private RestApiServer server;
    private static final int PORT = 18081;
    private HttpClient client;
    private SessionDAO sessionDAO;

    @BeforeEach
    void setUp() throws IOException {
        sessionDAO = new SessionDAOImpl();
        server = new RestApiServer(PORT);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Login issues session token, persists to MySQL session table, and invalid password rejects with 401")
    void testLoginTokenIssuingAndDatabasePersistence() throws Exception {
        // 1. Valid login as Alice (passenger)
        String loginBody = "{\"email\":\"passenger.alice@viame.cm\",\"password\":\"Password123!\"}";
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();

        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode());

        Map<String, String> json = JsonUtil.parseSimpleJson(loginRes.body());
        assertTrue(Boolean.parseBoolean(json.get("success")));
        String token = json.get("token");
        assertNotNull(token, "Response must contain session token");
        assertEquals("4", json.get("userId"));
        assertEquals("PASSENGER", json.get("role"));

        // 2. Verify session table in MySQL contains the hashed token
        String tokenHash = RestApiServer.hashToken(token);
        Optional<Session> dbSession = sessionDAO.findByTokenHash(tokenHash);
        assertTrue(dbSession.isPresent(), "Session must be persisted in MySQL session table");
        assertEquals(4L, dbSession.get().getUserId());

        // 3. Invalid credentials return 401
        HttpRequest badLoginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"passenger.alice@viame.cm\",\"password\":\"WrongPassword!\"}"))
                .build();

        HttpResponse<String> badRes = client.send(badLoginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, badRes.statusCode());
        assertTrue(badRes.body().contains("Invalid email or password"));

        // Clean up
        sessionDAO.deleteByTokenHash(tokenHash);
    }

    @Test
    @DisplayName("Protected endpoints enforce Bearer authentication and reject unauthenticated requests with 401")
    void testProtectedEndpointsRejectUnauthenticated() throws Exception {
        // Request without Authorization header
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=4"))
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode());
        assertTrue(res.body().contains("Authorization"));

        // Request with invalid token
        HttpRequest badTokenReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=4"))
                .header("Authorization", "Bearer invalid_non_existent_token_12345")
                .GET()
                .build();

        HttpResponse<String> badTokenRes = client.send(badTokenReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, badTokenRes.statusCode());
        assertTrue(badTokenRes.body().contains("Invalid session token"));
    }

    @Test
    @DisplayName("Rejects ID spoofing attempts with 403 Forbidden")
    void testAntiIdSpoofingProtection() throws Exception {
        // Login as Alice (id = 4)
        String loginBody = "{\"email\":\"passenger.alice@viame.cm\",\"password\":\"Password123!\"}";
        HttpResponse<String> loginRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build(), HttpResponse.BodyHandlers.ofString());

        String aliceToken = JsonUtil.parseSimpleJson(loginRes.body()).get("token");

        // Alice tries to access Bob's wallet balance (passengerId = 5)
        HttpRequest spoofBalanceReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=5"))
                .header("Authorization", "Bearer " + aliceToken)
                .GET()
                .build();

        HttpResponse<String> spoofRes = client.send(spoofBalanceReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoofRes.statusCode(), "Accessing another passenger's balance should be 403 Forbidden");
        assertTrue(spoofRes.body().contains("Forbidden"));

        // Alice accessing her own wallet balance succeeds (passengerId = 4)
        HttpRequest ownBalanceReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=4"))
                .header("Authorization", "Bearer " + aliceToken)
                .GET()
                .build();

        HttpResponse<String> ownRes = client.send(ownBalanceReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ownRes.statusCode());
        assertTrue(ownRes.body().contains("\"passengerId\":4"));

        // Alice tries to request a trip on behalf of passenger 5 (ID spoofing)
        String spoofTripJson = """
        {
            "passengerId": 5,
            "pickupLat": 4.051056,
            "pickupLng": 9.767869,
            "pickupAddress": "Akwa Palace",
            "dropoffLat": 4.006111,
            "dropoffLng": 9.719444,
            "dropoffAddress": "Airport",
            "paymentMethod": "WALLET"
        }
        """;
        HttpRequest spoofTripReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/trips/request"))
                .header("Authorization", "Bearer " + aliceToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(spoofTripJson))
                .build();

        HttpResponse<String> spoofTripRes = client.send(spoofTripReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoofTripRes.statusCode());
        assertTrue(spoofTripRes.body().contains("Cannot request trip for another passenger"));

        // Clean up
        sessionDAO.deleteByTokenHash(RestApiServer.hashToken(aliceToken));
    }

    @Test
    @DisplayName("Enforces RBAC role restrictions on admin-only report endpoints")
    void testRoleBasedAccessControlOnReports() throws Exception {
        // Login as Alice (Passenger)
        HttpResponse<String> aliceLogin = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"passenger.alice@viame.cm\",\"password\":\"Password123!\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        String aliceToken = JsonUtil.parseSimpleJson(aliceLogin.body()).get("token");

        // Alice calls admin report
        HttpResponse<String> reportAsAlice = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/reports/revenue"))
                .header("Authorization", "Bearer " + aliceToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, reportAsAlice.statusCode(), "Passenger should be forbidden from accessing reports");

        // Login as Admin
        HttpResponse<String> adminLogin = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"admin@viame.cm\",\"password\":\"Password123!\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        String adminToken = JsonUtil.parseSimpleJson(adminLogin.body()).get("token");

        // Admin calls admin report
        HttpResponse<String> reportAsAdmin = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/reports/revenue"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reportAsAdmin.statusCode(), "Admin should be allowed to view reports");
        assertTrue(reportAsAdmin.body().contains("\"rows\""));

        // Clean up
        sessionDAO.deleteByTokenHash(RestApiServer.hashToken(aliceToken));
        sessionDAO.deleteByTokenHash(RestApiServer.hashToken(adminToken));
    }

    @Test
    @DisplayName("Logout invalidates session in MySQL session table and prevents further requests")
    void testLogoutInvalidatesSession() throws Exception {
        // Login Alice
        HttpResponse<String> loginRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"passenger.alice@viame.cm\",\"password\":\"Password123!\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonUtil.parseSimpleJson(loginRes.body()).get("token");

        // Verify session works
        HttpResponse<String> beforeLogout = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=4"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, beforeLogout.statusCode());

        // Perform logout
        HttpResponse<String> logoutRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/auth/logout"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logoutRes.statusCode());
        assertTrue(logoutRes.body().contains("Logged out successfully"));

        // Verify database session row is removed
        assertTrue(sessionDAO.findByTokenHash(RestApiServer.hashToken(token)).isEmpty());

        // Subsequent call with same token is rejected with 401
        HttpResponse<String> afterLogout = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/wallet/balance?passengerId=4"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, afterLogout.statusCode());
    }

    @Test
    @DisplayName("Decimal formatting strictly uses dot delimiter regardless of non-US OS locale")
    void testLocaleIndependentDecimalFormatting() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        try {
            // Set locale to French (standard uses comma ',' as decimal separator)
            Locale.setDefault(Locale.FRANCE);

            String fareBody = """
            {
                "pickupLat": 4.051056,
                "pickupLng": 9.767869,
                "dropoffLat": 4.006111,
                "dropoffLng": 9.719444
            }
            """;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + "/api/fare/estimate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(fareBody))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());

            String body = res.body();
            // Verify numbers use dot '.' and not comma ','
            assertFalse(body.contains(",2") || body.contains(",5") || body.contains(",0"),
                    "JSON decimal output must not contain comma decimals in French locale: " + body);
            assertTrue(body.matches(".*\"distanceKm\":\\d+\\.\\d{2}.*"),
                    "distanceKm must be formatted with dot delimiter: " + body);
            assertTrue(body.matches(".*\"totalAmount\":\\d+\\.\\d{2}.*"),
                    "totalAmount must be formatted with dot delimiter: " + body);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}

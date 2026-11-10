package com.viame.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RestApiServer HTTP Integration Test")
public class RestApiServerTest {

    private RestApiServer server;
    private static final int TEST_PORT = 18080;

    @BeforeEach
    public void startServer() throws IOException {
        server = new RestApiServer(TEST_PORT);
        server.start();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Health check endpoint returns status UP")
    public void testHealthEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    @DisplayName("Avatar endpoint returns valid animated SVG with proper headers")
    public void testAvatarEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/avatar?seed=Samuel&mood=cool&size=96"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("image/svg+xml"));
        assertTrue(response.body().contains("<svg"));
        assertTrue(response.body().contains("</svg>"));
        assertTrue(response.body().contains("@keyframes mimo-blink"));
    }

    @Test
    @DisplayName("CORS OPTIONS preflight returns 204 with required headers across endpoints")
    public void testCorsPreflightOptions() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/trips/request"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("Content-Type"));
    }

    @Test
    @DisplayName("Wallet balance endpoint validates required passengerId query parameter")
    public void testWalletBalanceParameterValidation() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/wallet/balance"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Missing passengerId query parameter"));
    }

    @Test
    @DisplayName("Trip status endpoint validates required tripId or passengerId query parameters")
    public void testTripStatusParameterValidation() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/trips/status"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Missing tripId or passengerId query parameter"));
    }

    @Test
    @DisplayName("JSON parser safely handles string values with commas and colons")
    public void testJsonParserWithCommasAndColons() {
        String json = "{\"pickupAddress\":\"Akwa Palace Hotel, Douala\",\"phone\":\"+237 670:000\",\"passengerId\":1,\"amount\":5000.00}";
        Map<String, String> map = RestApiServer.parseSimpleJson(json);
        assertEquals("Akwa Palace Hotel, Douala", map.get("pickupAddress"));
        assertEquals("+237 670:000", map.get("phone"));
        assertEquals("1", map.get("passengerId"));
        assertEquals("5000.00", map.get("amount"));
    }
}

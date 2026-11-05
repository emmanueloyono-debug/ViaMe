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
}

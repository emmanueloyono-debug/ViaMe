package com.viame.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.viame.exception.AuthenticationException;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Trip;
import com.viame.model.User;
import com.viame.model.enums.PaymentMethod;
import com.viame.service.AuthenticationService;
import com.viame.service.DriverMatchingService;
import com.viame.service.FareCalculationService;
import com.viame.service.FareQuote;
import com.viame.service.PaymentService;
import com.viame.service.ReportService;
import com.viame.service.TripService;
import com.viame.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Built-in Lightweight HTTP REST API Server for Android Integration.
 * Fulfills the Project 5 requirement:
 * "The Android app must not connect directly to MySQL. Use Android -> REST API -> Java backend -> MySQL."
 *
 * Runs on standard JDK com.sun.net.httpserver without external bloat.
 */
public class RestApiServer {

    private final int port;
    private HttpServer server;
    private ExecutorService executor;

    private final AuthenticationService authService = new AuthenticationService();
    private final TripService tripService = new TripService();
    private final FareCalculationService fareService = new FareCalculationService();
    private final PaymentService paymentService = new PaymentService();
    private final DriverMatchingService driverMatchingService = new DriverMatchingService();
    private final ReportService reportService = new ReportService();

    @FunctionalInterface
    private interface ReportQuery {
        List<? extends Record> run(LocalDate from, LocalDate to, Map<String, String> params);
    }

    /**
     * Generic GET handler for BI reports. Defaults to the last 30 days when no range is given.
     * Response: {"from":..,"to":..,"count":n,"rows":[...]}
     */
    private class ReportHandler implements HttpHandler {
        private final ReportQuery query;

        ReportHandler(ReportQuery query) {
            this.query = query;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                LocalDate to = params.containsKey("to") ? LocalDate.parse(params.get("to")) : LocalDate.now();
                LocalDate from = params.containsKey("from") ? LocalDate.parse(params.get("from")) : to.minusDays(30);
                List<? extends Record> rows = query.run(from, to, params);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("from", from);
                body.put("to", to);
                body.put("count", rows.size());
                body.put("rows", rows);
                sendResponse(exchange, 200, JsonUtil.toJson(body));
            } catch (DateTimeParseException | NumberFormatException e) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid parameter: " + escapeJson(e.getMessage()) + "\"}");
            } catch (ValidationException e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    public RestApiServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newFixedThreadPool(10, r -> {
            Thread t = new Thread(r, "viame-http-worker");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);

        // Health check endpoint
        server.createContext("/api/health", exchange -> {
            sendResponse(exchange, 200, "{\"status\":\"UP\",\"system\":\"ViaMe Transport API\"}");
        });

        // 1. Authentication Endpoints
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register/passenger", new RegisterPassengerHandler());
        server.createContext("/api/auth/register/driver", new RegisterDriverHandler());

        // 2. Fare & Dispatch Endpoints
        server.createContext("/api/fare/estimate", new FareEstimateHandler());
        server.createContext("/api/drivers/available", new AvailableDriversHandler());

        // 3. Trip Lifecycle Endpoints
        server.createContext("/api/trips/request", new RequestTripHandler());
        server.createContext("/api/trips/accept", new AcceptTripHandler());
        server.createContext("/api/trips/status", new UpdateTripStatusHandler());
        server.createContext("/api/trips/cancel", new CancelTripHandler());

        // 4. Wallet Endpoints
        server.createContext("/api/wallet/topup", new WalletTopupHandler());

        // 5. Business Intelligence Report Endpoints (GET ?from=YYYY-MM-DD&to=YYYY-MM-DD)
        server.createContext("/api/reports/revenue", new ReportHandler((f, t, p) -> reportService.dailyRevenue(f, t)));
        server.createContext("/api/reports/drivers", new ReportHandler((f, t, p) -> reportService.driverPerformance(f, t)));
        server.createContext("/api/reports/peak-hours", new ReportHandler((f, t, p) -> reportService.peakHours(f, t)));
        server.createContext("/api/reports/pickup-zones", new ReportHandler((f, t, p) ->
                reportService.topPickupZones(f, t, Integer.parseInt(p.getOrDefault("limit", "10")))));
        server.createContext("/api/reports/cancellations", new ReportHandler((f, t, p) -> reportService.cancellations(f, t)));
        server.createContext("/api/reports/loyalty", new ReportHandler((f, t, p) -> reportService.customerLoyalty(f, t)));

        server.start();
        System.out.println("ViaMe REST API Server listening on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // --- Endpoint Handlers ---

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                String email = json.get("email");
                String password = json.get("password");

                User user = authService.login(email, password);
                String res = String.format("{\"success\":true,\"userId\":%d,\"email\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}",
                        user.getId(), user.getEmail(), user.getRole(), user.getStatus());
                sendResponse(exchange, 200, res);
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class RegisterPassengerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                Passenger p = authService.registerPassenger(
                        json.get("email"), json.get("phone"), json.get("password"),
                        json.get("fullName"), PaymentMethod.valueOf(json.getOrDefault("paymentMethod", "WALLET")));
                String res = String.format("{\"success\":true,\"userId\":%d,\"fullName\":\"%s\",\"walletBalance\":%.2f}",
                        p.getId(), p.getFullName(), p.getWalletBalance());
                sendResponse(exchange, 201, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class RegisterDriverHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                int seats = Integer.parseInt(json.getOrDefault("seats", "4"));
                Driver d = authService.registerDriver(
                        json.get("email"), json.get("phone"), json.get("password"),
                        json.get("fullName"), json.get("licenceNumber"), json.get("plateNumber"),
                        json.get("make"), json.get("model"), seats);
                String res = String.format("{\"success\":true,\"userId\":%d,\"verificationStatus\":\"%s\"}",
                        d.getId(), d.getVerificationStatus());
                sendResponse(exchange, 201, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class FareEstimateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                double pLat = Double.parseDouble(json.get("pickupLat"));
                double pLng = Double.parseDouble(json.get("pickupLng"));
                double dLat = Double.parseDouble(json.get("dropoffLat"));
                double dLng = Double.parseDouble(json.get("dropoffLng"));

                FareQuote quote = fareService.estimate(pLat, pLng, dLat, dLng);
                String res = String.format("{\"distanceKm\":%.2f,\"durationMin\":%d,\"baseFare\":%.2f,\"surge\":%.2f,\"totalAmount\":%.2f,\"currency\":\"%s\"}",
                        quote.distanceKm(), quote.durationMin(), quote.baseFare(), quote.surgeMultiplier(), quote.totalAmount(), quote.currency());
                sendResponse(exchange, 200, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AvailableDriversHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                double lat = Double.parseDouble(params.getOrDefault("lat", "4.051056"));
                double lng = Double.parseDouble(params.getOrDefault("lng", "9.767869"));
                double radius = Double.parseDouble(params.getOrDefault("radius", "5.0"));

                List<DriverMatchingService.DriverCandidate> candidates = driverMatchingService.findCandidates(lat, lng, radius);
                StringBuilder sb = new StringBuilder("{\"count\":").append(candidates.size()).append(",\"drivers\":[");
                for (int i = 0; i < candidates.size(); i++) {
                    DriverMatchingService.DriverCandidate c = candidates.get(i);
                    sb.append(String.format("{\"driverId\":%d,\"name\":\"%s\",\"distanceKm\":%.2f,\"rating\":%.2f}",
                            c.driver().getId(), c.driver().getFullName(), c.distanceKm(), c.driver().getRatingAvg()));
                    if (i < candidates.size() - 1) sb.append(",");
                }
                sb.append("]}");
                sendResponse(exchange, 200, sb.toString());
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class RequestTripHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long passengerId = Long.parseLong(json.get("passengerId"));
                double pLat = Double.parseDouble(json.get("pickupLat"));
                double pLng = Double.parseDouble(json.get("pickupLng"));
                String pAddr = json.get("pickupAddress");
                double dLat = Double.parseDouble(json.get("dropoffLat"));
                double dLng = Double.parseDouble(json.get("dropoffLng"));
                String dAddr = json.get("dropoffAddress");
                PaymentMethod method = PaymentMethod.valueOf(json.getOrDefault("paymentMethod", "WALLET"));

                Trip trip = tripService.requestTrip(passengerId, pLat, pLng, pAddr, dLat, dLng, dAddr, method);
                String res = String.format("{\"success\":true,\"tripId\":%d,\"status\":\"%s\",\"distanceKm\":%.2f,\"durationMin\":%d}",
                        trip.getId(), trip.getStatus(), trip.getDistanceKm(), trip.getDurationMin());
                sendResponse(exchange, 201, res);
            } catch (InsufficientFundsException e) {
                sendResponse(exchange, 402, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AcceptTripHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long tripId = Long.parseLong(json.get("tripId"));
                long driverId = Long.parseLong(json.get("driverId"));

                Trip trip = tripService.acceptTrip(tripId, driverId);
                String res = String.format("{\"success\":true,\"tripId\":%d,\"status\":\"%s\",\"driverId\":%d}",
                        trip.getId(), trip.getStatus(), trip.getDriverId());
                sendResponse(exchange, 200, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class UpdateTripStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long tripId = Long.parseLong(json.get("tripId"));
                long driverId = Long.parseLong(json.get("driverId"));
                String action = json.get("action"); // EN_ROUTE, ARRIVED, START, COMPLETE

                Trip trip = switch (action.toUpperCase()) {
                    case "EN_ROUTE" -> tripService.driverEnRoute(tripId, driverId);
                    case "ARRIVED" -> tripService.driverArrived(tripId, driverId);
                    case "START" -> tripService.startTrip(tripId, driverId);
                    case "COMPLETE" -> tripService.completeTrip(tripId, driverId,
                            PaymentMethod.valueOf(json.getOrDefault("paymentMethod", "WALLET")));
                    default -> throw new ValidationException("Unknown action: " + action);
                };

                String res = String.format("{\"success\":true,\"tripId\":%d,\"status\":\"%s\"}", trip.getId(), trip.getStatus());
                sendResponse(exchange, 200, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CancelTripHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long tripId = Long.parseLong(json.get("tripId"));
                long userId = Long.parseLong(json.get("userId"));
                boolean isPassenger = Boolean.parseBoolean(json.getOrDefault("isPassenger", "true"));

                Trip trip = tripService.cancelTrip(tripId, userId, isPassenger);
                String res = String.format("{\"success\":true,\"tripId\":%d,\"status\":\"%s\"}", trip.getId(), trip.getStatus());
                sendResponse(exchange, 200, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class WalletTopupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long passengerId = Long.parseLong(json.get("passengerId"));
                BigDecimal amount = new BigDecimal(json.get("amount"));
                String provider = json.getOrDefault("provider", "MOBILE_MONEY");

                BigDecimal newBalance = paymentService.topUpWallet(passengerId, amount, provider);
                String res = String.format("{\"success\":true,\"passengerId\":%d,\"newBalance\":%.2f}", passengerId, newBalance);
                sendResponse(exchange, 200, res);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // --- Helpers ---

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        String clean = json.trim().replaceAll("^\\{|\\}$", "");
        String[] pairs = clean.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String val = kv[1].trim().replace("\"", "");
                map.put(key, val);
            }
        }
        return map;
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        RestApiServer api = new RestApiServer(port);
        api.start();
    }
}


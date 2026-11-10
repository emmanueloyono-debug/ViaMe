package com.viame.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.viame.dao.DriverDAO;
import com.viame.dao.PassengerDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.exception.AuthenticationException;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Rating;
import com.viame.model.Trip;
import com.viame.model.User;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.TripStatus;
import com.viame.service.AuthenticationService;
import com.viame.service.DriverMatchingService;
import com.viame.service.FareCalculationService;
import com.viame.service.FareQuote;
import com.viame.service.PaymentService;
import com.viame.service.RatingService;
import com.viame.service.ReportService;
import com.viame.service.TripService;
import com.viame.util.AvatarUtil;
import com.viame.util.JsonUtil;
import com.viame.util.MimoAvatarGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Built-in Lightweight HTTP REST API Server for Android and Mobile PWA Integration.
 * Fulfills the Project 5 requirement:
 * "The Android app must not connect directly to MySQL. Use Android -> REST API -> Java backend -> MySQL."
 *
 * Runs on standard JDK com.sun.net.httpserver without external bloat.
 * Fully supports CORS preflight (OPTIONS) for seamless browser and native mobile consumption.
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
    private final RatingService ratingService = new RatingService();
    private final DriverDAO driverDAO = new DriverDAOImpl();
    private final PassengerDAO passengerDAO = new PassengerDAOImpl();

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
            if (handlePreflight(exchange)) return;
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
            if (handlePreflight(exchange)) return;
            sendResponse(exchange, 200, "{\"status\":\"UP\",\"system\":\"ViaMe Transport API\"}");
        });

        // 1. Authentication Endpoints
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register/passenger", new RegisterPassengerHandler());
        server.createContext("/api/auth/register/driver", new RegisterDriverHandler());

        // 2. Fare & Dispatch Endpoints
        server.createContext("/api/fare/estimate", new FareEstimateHandler());
        server.createContext("/api/drivers/available", new AvailableDriversHandler());
        server.createContext("/api/drivers/availability", new DriverAvailabilityHandler());
        server.createContext("/api/drivers/earnings", new DriverEarningsHandler());

        // 3. Trip Lifecycle Endpoints
        server.createContext("/api/trips/request", new RequestTripHandler());
        server.createContext("/api/trips/accept", new AcceptTripHandler());
        server.createContext("/api/trips/status", new TripStatusHandler());
        server.createContext("/api/trips/get", new TripStatusHandler());
        server.createContext("/api/trips/pending", new PendingTripsHandler());
        server.createContext("/api/trips/cancel", new CancelTripHandler());

        // 4. Wallet Endpoints
        server.createContext("/api/wallet/topup", new WalletTopupHandler());
        server.createContext("/api/wallet/balance", new WalletBalanceHandler());

        // 5. Ratings Endpoint
        server.createContext("/api/ratings", new RatingHandler());

        // 6. Business Intelligence Report Endpoints (GET ?from=YYYY-MM-DD&to=YYYY-MM-DD)
        server.createContext("/api/reports/revenue", new ReportHandler((f, t, p) -> reportService.dailyRevenue(f, t)));
        server.createContext("/api/reports/drivers", new ReportHandler((f, t, p) -> reportService.driverPerformance(f, t)));
        server.createContext("/api/reports/peak-hours", new ReportHandler((f, t, p) -> reportService.peakHours(f, t)));
        server.createContext("/api/reports/pickup-zones", new ReportHandler((f, t, p) ->
                reportService.topPickupZones(f, t, Integer.parseInt(p.getOrDefault("limit", "10")))));
        server.createContext("/api/reports/cancellations", new ReportHandler((f, t, p) -> reportService.cancellations(f, t)));
        server.createContext("/api/reports/loyalty", new ReportHandler((f, t, p) -> reportService.customerLoyalty(f, t)));

        // 7. Native Mimo Avatar Generation Endpoint (GET ?seed=...&mood=...&size=...&shape=...)
        server.createContext("/api/avatar", new AvatarHandler());

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
            if (handlePreflight(exchange)) return;
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
                MimoAvatarGenerator.Mood userMood = (user.getRole() == com.viame.model.enums.UserRole.DRIVER)
                        ? MimoAvatarGenerator.Mood.COOL : MimoAvatarGenerator.Mood.HAPPY;
                String avatarUrl = AvatarUtil.getLocalApiUrl(user.getEmail(), userMood);
                String res = String.format("{\"success\":true,\"userId\":%d,\"email\":\"%s\",\"role\":\"%s\",\"status\":\"%s\",\"avatarUrl\":\"%s\"}",
                        user.getId(), user.getEmail(), user.getRole(), user.getStatus(), avatarUrl);
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
            if (handlePreflight(exchange)) return;
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
            if (handlePreflight(exchange)) return;
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
            if (handlePreflight(exchange)) return;
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
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                double lat = Double.parseDouble(params.getOrDefault("lat", "4.051056"));
                double lng = Double.parseDouble(params.getOrDefault("lng", "9.767869"));
                double radius = Double.parseDouble(params.getOrDefault("radius", "5.0"));

                List<DriverMatchingService.DriverCandidate> candidates = driverMatchingService.findCandidates(lat, lng, radius);
                StringBuilder sb = new StringBuilder("{\"count\":").append(candidates.size()).append(",\"drivers\":[");
                for (int i = 0; i < candidates.size(); i++) {
                    DriverMatchingService.DriverCandidate c = candidates.get(i);
                    String avatarUrl = AvatarUtil.getLocalApiUrl(c.driver().getFullName(), MimoAvatarGenerator.Mood.COOL);
                    sb.append(String.format("{\"driverId\":%d,\"name\":\"%s\",\"distanceKm\":%.2f,\"rating\":%.2f,\"avatarUrl\":\"%s\"}",
                            c.driver().getId(), c.driver().getFullName(), c.distanceKm(), c.driver().getRatingAvg(), avatarUrl));
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
            if (handlePreflight(exchange)) return;
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
            if (handlePreflight(exchange)) return;
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

    /**
     * Handles both status progression (POST) and status query by tripId or passengerId (GET).
     */
    private class TripStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    Optional<Trip> optTrip;
                    if (params.containsKey("tripId")) {
                        optTrip = tripService.getTripById(Long.parseLong(params.get("tripId")));
                    } else if (params.containsKey("passengerId")) {
                        optTrip = tripService.getActiveTripForPassenger(Long.parseLong(params.get("passengerId")));
                    } else {
                        sendResponse(exchange, 400, "{\"error\":\"Missing tripId or passengerId query parameter\"}");
                        return;
                    }

                    if (optTrip.isEmpty()) {
                        sendResponse(exchange, 404, "{\"error\":\"Trip not found\"}");
                        return;
                    }
                    sendResponse(exchange, 200, formatTripJson(optTrip.get()));
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
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

                    sendResponse(exchange, 200, formatTripJson(trip));
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    private class PendingTripsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                List<Trip> pending = tripService.getPendingBroadcastRequests();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("count", pending.size());
                List<Map<String, Object>> tripList = new ArrayList<>();
                for (Trip t : pending) {
                    tripList.add(formatTripSummary(t));
                }
                body.put("trips", tripList);
                sendResponse(exchange, 200, JsonUtil.toJson(body));
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CancelTripHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
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
                sendResponse(exchange, 200, formatTripJson(trip));
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class WalletTopupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
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

    private class WalletBalanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                if (!params.containsKey("passengerId")) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing passengerId query parameter\"}");
                    return;
                }
                long passengerId = Long.parseLong(params.get("passengerId"));
                Optional<Passenger> opt = passengerDAO.findById(passengerId);
                if (opt.isEmpty()) {
                    sendResponse(exchange, 404, "{\"error\":\"Passenger not found\"}");
                    return;
                }
                BigDecimal balance = opt.get().getWalletBalance();
                sendResponse(exchange, 200, String.format("{\"success\":true,\"passengerId\":%d,\"balance\":%.2f}", passengerId, balance));
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class DriverAvailabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, String> json = parseSimpleJson(readBody(exchange));
                    long driverId = Long.parseLong(json.get("driverId"));
                    String statusStr = json.get("status");
                    if (statusStr == null || statusStr.isBlank()) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing status parameter\"}");
                        return;
                    }
                    DriverAvailability availability = DriverAvailability.valueOf(statusStr.toUpperCase());
                    boolean updated = driverDAO.updateAvailability(driverId, availability);
                    if (!updated) {
                        sendResponse(exchange, 404, "{\"error\":\"Driver not found\"}");
                        return;
                    }
                    sendResponse(exchange, 200, String.format("{\"success\":true,\"driverId\":%d,\"status\":\"%s\"}", driverId, availability));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid status value. Must be ONLINE or OFFLINE\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    if (!params.containsKey("driverId")) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing driverId query parameter\"}");
                        return;
                    }
                    long driverId = Long.parseLong(params.get("driverId"));
                    Optional<Driver> opt = driverDAO.findById(driverId);
                    if (opt.isEmpty()) {
                        sendResponse(exchange, 404, "{\"error\":\"Driver not found\"}");
                        return;
                    }
                    sendResponse(exchange, 200, String.format("{\"success\":true,\"driverId\":%d,\"status\":\"%s\"}", driverId, opt.get().getAvailability()));
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    private class DriverEarningsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                if (!params.containsKey("driverId")) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing driverId query parameter\"}");
                    return;
                }
                long driverId = Long.parseLong(params.get("driverId"));
                Optional<Driver> opt = driverDAO.findById(driverId);
                if (opt.isEmpty()) {
                    sendResponse(exchange, 404, "{\"error\":\"Driver not found\"}");
                    return;
                }
                Driver driver = opt.get();
                List<Trip> trips = tripService.getTripsForDriver(driverId);
                long completedCount = trips.stream().filter(t -> t.getStatus() == TripStatus.COMPLETED).count();
                BigDecimal netEarnings = driver.getTotalEarnings() != null ? driver.getTotalEarnings() : BigDecimal.ZERO;
                BigDecimal gross = netEarnings.divide(new BigDecimal("0.80"), 2, RoundingMode.HALF_UP);
                BigDecimal commission = gross.subtract(netEarnings);
                sendResponse(exchange, 200, String.format(
                        "{\"success\":true,\"driverId\":%d,\"netEarnings\":%.2f,\"commission\":%.2f,\"grossRevenue\":%.2f,\"completedTrips\":%d,\"ratingAvg\":%.2f,\"availability\":\"%s\"}",
                        driverId, netEarnings, commission, gross, completedCount, driver.getRatingAvg(), driver.getAvailability()));
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class RatingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, String> json = parseSimpleJson(readBody(exchange));
                    long tripId = Long.parseLong(json.get("tripId"));
                    long raterId = Long.parseLong(json.get("raterId"));
                    long rateeId = Long.parseLong(json.get("rateeId"));
                    int score = Integer.parseInt(json.get("score"));
                    String comment = json.getOrDefault("comment", "");

                    Rating rating = ratingService.submitRating(tripId, raterId, rateeId, score, comment);
                    sendResponse(exchange, 201, String.format("{\"success\":true,\"ratingId\":%d,\"score\":%d}",
                            rating.getId(), rating.getScore()));
                } catch (ValidationException e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    if (!params.containsKey("userId")) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing userId query parameter\"}");
                        return;
                    }
                    long userId = Long.parseLong(params.get("userId"));
                    List<Rating> ratings = ratingService.getRatingsForUser(userId);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("userId", userId);
                    body.put("count", ratings.size());
                    body.put("ratings", ratings);
                    sendResponse(exchange, 200, JsonUtil.toJson(body));
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    private class AvatarHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String seed = params.getOrDefault("seed", params.getOrDefault("name", "viame"));
                int size = 128;
                if (params.containsKey("size")) {
                    try {
                        size = Integer.parseInt(params.get("size"));
                    } catch (NumberFormatException ignored) {}
                }
                String moodStr = params.get("mood");
                MimoAvatarGenerator.Mood mood = null;
                if (moodStr != null) {
                    try {
                        mood = MimoAvatarGenerator.Mood.valueOf(moodStr.toUpperCase());
                    } catch (IllegalArgumentException ignored) {}
                }
                String shapeStr = params.get("shape");
                MimoAvatarGenerator.Shape shape = MimoAvatarGenerator.Shape.CIRCLE;
                if (shapeStr != null) {
                    try {
                        shape = MimoAvatarGenerator.Shape.valueOf(shapeStr.toUpperCase());
                    } catch (IllegalArgumentException ignored) {}
                }
                boolean animated = !"false".equalsIgnoreCase(params.get("animated"));

                String svg = MimoAvatarGenerator.generateSvg(seed, size, shape, mood, animated);
                byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "image/svg+xml; charset=utf-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // --- Helpers ---

    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String formatTripJson(Trip trip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("tripId", trip.getId());
        map.put("passengerId", trip.getPassengerId());
        map.put("driverId", trip.getDriverId());
        map.put("vehicleId", trip.getVehicleId());
        map.put("status", trip.getStatus() != null ? trip.getStatus().name() : null);
        map.put("pickupLat", trip.getPickupLat());
        map.put("pickupLng", trip.getPickupLng());
        map.put("pickupAddress", trip.getPickupAddress());
        map.put("dropoffLat", trip.getDropoffLat());
        map.put("dropoffLng", trip.getDropoffLng());
        map.put("dropoffAddress", trip.getDropoffAddress());
        map.put("distanceKm", trip.getDistanceKm());
        map.put("durationMin", trip.getDurationMin());
        map.put("requestedAt", trip.getRequestedAt() != null ? trip.getRequestedAt().toString() : null);
        map.put("acceptedAt", trip.getAcceptedAt() != null ? trip.getAcceptedAt().toString() : null);
        map.put("startedAt", trip.getStartedAt() != null ? trip.getStartedAt().toString() : null);
        map.put("completedAt", trip.getCompletedAt() != null ? trip.getCompletedAt().toString() : null);
        map.put("cancelledAt", trip.getCancelledAt() != null ? trip.getCancelledAt().toString() : null);
        return JsonUtil.toJson(map);
    }

    private static Map<String, Object> formatTripSummary(Trip t) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tripId", t.getId());
        item.put("passengerId", t.getPassengerId());
        item.put("driverId", t.getDriverId());
        item.put("pickupAddress", t.getPickupAddress());
        item.put("dropoffAddress", t.getDropoffAddress());
        item.put("pickupLat", t.getPickupLat());
        item.put("pickupLng", t.getPickupLng());
        item.put("dropoffLat", t.getDropoffLat());
        item.put("dropoffLng", t.getDropoffLng());
        item.put("distanceKm", t.getDistanceKm());
        item.put("durationMin", t.getDurationMin());
        item.put("status", t.getStatus() != null ? t.getStatus().name() : null);
        item.put("requestedAt", t.getRequestedAt() != null ? t.getRequestedAt().toString() : null);
        return item;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Robust, dependency-free JSON object parser.
     * Safely handles string values with commas, colons, spaces, and escaped characters.
     */
    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        int len = s.length();
        int i = 0;
        while (i < len) {
            while (i < len && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) i++;
            if (i >= len) break;

            String key;
            if (s.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < len) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                key = sb.toString();
            } else {
                int start = i;
                while (i < len && s.charAt(i) != ':' && !Character.isWhitespace(s.charAt(i))) i++;
                key = s.substring(start, i).trim();
            }

            while (i < len && s.charAt(i) != ':') i++;
            if (i < len && s.charAt(i) == ':') i++;

            while (i < len && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= len) break;

            String val;
            if (s.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < len) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                val = sb.toString();
            } else {
                int start = i;
                while (i < len && s.charAt(i) != ',' && s.charAt(i) != '}') i++;
                val = s.substring(start, i).trim();
            }

            map.put(key, val);
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

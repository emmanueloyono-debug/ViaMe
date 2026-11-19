package com.viame.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.viame.dao.DriverDAO;
import com.viame.dao.PassengerDAO;
import com.viame.dao.SessionDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.SessionDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.exception.AuthenticationException;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.Rating;
import com.viame.model.Session;
import com.viame.model.Trip;
import com.viame.model.User;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;
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
import com.viame.util.SessionManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * Enforces session token authentication and RBAC authorization with anti-ID spoofing protection.
 */
public class RestApiServer {

    private final int port;
    private HttpServer server;
    private ExecutorService executor;

    private final AuthenticationService authService;
    private final TripService tripService;
    private final FareCalculationService fareService;
    private final PaymentService paymentService;
    private final DriverMatchingService driverMatchingService;
    private final ReportService reportService;
    private final RatingService ratingService;
    private final DriverDAO driverDAO;
    private final PassengerDAO passengerDAO;
    private final UserDAO userDAO;
    private final SessionDAO sessionDAO;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @FunctionalInterface
    private interface ReportQuery {
        List<? extends Record> run(LocalDate from, LocalDate to, Map<String, String> params);
    }

    /**
     * Generic GET handler for BI reports. Defaults to the last 30 days when no range is given.
     * Protected: requires ADMIN role.
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
                User authUser = authenticate(exchange);
                if (authUser.getRole() != UserRole.ADMIN) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Admin role required to access reports\"}");
                    return;
                }

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
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
        this(port, new AuthenticationService(), new TripService(), new FareCalculationService(),
                new PaymentService(), new DriverMatchingService(), new ReportService(),
                new RatingService(), new DriverDAOImpl(), new PassengerDAOImpl(),
                new UserDAOImpl(), new SessionDAOImpl());
    }

    public RestApiServer(int port, AuthenticationService authService, TripService tripService,
                         FareCalculationService fareService, PaymentService paymentService,
                         DriverMatchingService driverMatchingService, ReportService reportService,
                         RatingService ratingService, DriverDAO driverDAO, PassengerDAO passengerDAO,
                         UserDAO userDAO, SessionDAO sessionDAO) {
        this.port = port;
        this.authService = authService;
        this.tripService = tripService;
        this.fareService = fareService;
        this.paymentService = paymentService;
        this.driverMatchingService = driverMatchingService;
        this.reportService = reportService;
        this.ratingService = ratingService;
        this.driverDAO = driverDAO;
        this.passengerDAO = passengerDAO;
        this.userDAO = userDAO;
        this.sessionDAO = sessionDAO;
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
        server.createContext("/api/auth/logout", new LogoutHandler());
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

                // Issue REST session token and store in database session table
                String rawToken = generateToken();
                String tokenHash = hashToken(rawToken);
                Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

                String clientIp = null;
                if (exchange.getRequestHeaders().containsKey("X-Forwarded-For")) {
                    clientIp = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
                } else if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
                    clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                }
                if (clientIp != null && clientIp.length() > 45) {
                    clientIp = clientIp.substring(0, 45);
                }

                Session session = new Session(tokenHash, user.getId(), expiresAt, clientIp);
                sessionDAO.save(session);

                // Bind to SessionManager for per-token session isolation
                SessionManager.getInstance().login(rawToken, user);
                SessionManager.getInstance().login(tokenHash, user);

                MimoAvatarGenerator.Mood userMood = (user.getRole() == UserRole.DRIVER)
                        ? MimoAvatarGenerator.Mood.COOL : MimoAvatarGenerator.Mood.HAPPY;
                String avatarUrl = AvatarUtil.getLocalApiUrl(user.getEmail(), userMood);

                String res = String.format(Locale.US,
                        "{\"success\":true,\"token\":\"%s\",\"sessionToken\":\"%s\",\"userId\":%d,\"email\":\"%s\",\"role\":\"%s\",\"status\":\"%s\",\"avatarUrl\":\"%s\",\"expiresAt\":\"%s\"}",
                        rawToken, rawToken, user.getId(), user.getEmail(), user.getRole(), user.getStatus(), avatarUrl, expiresAt);
                sendResponse(exchange, 200, res);
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                String token = extractBearerToken(exchange);
                if (token != null && !token.isBlank()) {
                    String tokenHash = hashToken(token);
                    sessionDAO.deleteByTokenHash(tokenHash);
                    sessionDAO.deleteByTokenHash(token);
                    SessionManager.getInstance().logout(token);
                    SessionManager.getInstance().logout(tokenHash);
                }
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Logged out successfully\"}");
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
                String res = String.format(Locale.US, "{\"success\":true,\"userId\":%d,\"fullName\":\"%s\",\"walletBalance\":%.2f}",
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
                String res = String.format(Locale.US, "{\"success\":true,\"userId\":%d,\"verificationStatus\":\"%s\"}",
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
                String res = String.format(Locale.US,
                        "{\"distanceKm\":%.2f,\"durationMin\":%d,\"baseFare\":%.2f,\"surge\":%.2f,\"totalAmount\":%.2f,\"currency\":\"%s\"}",
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
                    sb.append(String.format(Locale.US, "{\"driverId\":%d,\"name\":\"%s\",\"distanceKm\":%.2f,\"rating\":%.2f,\"avatarUrl\":\"%s\"}",
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
                User authUser = authenticate(exchange);
                if (authUser.getRole() != UserRole.PASSENGER && authUser.getRole() != UserRole.ADMIN) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only passengers can request trips\"}");
                    return;
                }

                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long passengerId = Long.parseLong(json.get("passengerId"));

                // Anti-spoofing: passenger can only request trips for their own account
                if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != passengerId) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot request trip for another passenger (ID spoofing detected)\"}");
                    return;
                }

                double pLat = Double.parseDouble(json.get("pickupLat"));
                double pLng = Double.parseDouble(json.get("pickupLng"));
                String pAddr = json.get("pickupAddress");
                double dLat = Double.parseDouble(json.get("dropoffLat"));
                double dLng = Double.parseDouble(json.get("dropoffLng"));
                String dAddr = json.get("dropoffAddress");
                PaymentMethod method = PaymentMethod.valueOf(json.getOrDefault("paymentMethod", "WALLET"));

                Trip trip = tripService.requestTrip(passengerId, pLat, pLng, pAddr, dLat, dLng, dAddr, method);
                String res = String.format(Locale.US, "{\"success\":true,\"tripId\":%d,\"status\":\"%s\",\"distanceKm\":%.2f,\"durationMin\":%d}",
                        trip.getId(), trip.getStatus(), trip.getDistanceKm(), trip.getDurationMin());
                sendResponse(exchange, 201, res);
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                User authUser = authenticate(exchange);
                if (authUser.getRole() != UserRole.DRIVER && authUser.getRole() != UserRole.ADMIN) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only drivers can accept trips\"}");
                    return;
                }

                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long tripId = Long.parseLong(json.get("tripId"));
                long driverId = Long.parseLong(json.get("driverId"));

                // Anti-spoofing: driver can only accept trips for themselves
                if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != driverId) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot accept trip for another driver (ID spoofing detected)\"}");
                    return;
                }

                Trip trip = tripService.acceptTrip(tripId, driverId);
                String res = String.format(Locale.US, "{\"success\":true,\"tripId\":%d,\"status\":\"%s\",\"driverId\":%d}",
                        trip.getId(), trip.getStatus(), trip.getDriverId());
                sendResponse(exchange, 200, res);
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                    if (!params.containsKey("tripId") && !params.containsKey("passengerId")) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing tripId or passengerId query parameter\"}");
                        return;
                    }

                    User authUser = authenticate(exchange);

                    Optional<Trip> optTrip;
                    if (params.containsKey("tripId")) {
                        long tripId = Long.parseLong(params.get("tripId"));
                        optTrip = tripService.getTripById(tripId);
                        if (optTrip.isPresent()) {
                            Trip trip = optTrip.get();
                            // Anti-spoofing: user must be admin, or the passenger/driver of the trip
                            boolean isParty = authUser.getId() == trip.getPassengerId() ||
                                    (trip.getDriverId() != null && authUser.getId() == trip.getDriverId());
                            if (authUser.getRole() != UserRole.ADMIN && !isParty) {
                                sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot view trip details for other users\"}");
                                return;
                            }
                        }
                    } else {
                        long passengerId = Long.parseLong(params.get("passengerId"));
                        if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != passengerId) {
                            sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot view active trips for other passengers\"}");
                            return;
                        }
                        optTrip = tripService.getActiveTripForPassenger(passengerId);
                    }

                    if (optTrip.isEmpty()) {
                        sendResponse(exchange, 404, "{\"error\":\"Trip not found\"}");
                        return;
                    }
                    sendResponse(exchange, 200, formatTripJson(optTrip.get()));
                } catch (AuthenticationException e) {
                    sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    User authUser = authenticate(exchange);
                    if (authUser.getRole() != UserRole.DRIVER && authUser.getRole() != UserRole.ADMIN) {
                        sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only drivers can advance trip status\"}");
                        return;
                    }

                    Map<String, String> json = parseSimpleJson(readBody(exchange));
                    long tripId = Long.parseLong(json.get("tripId"));
                    long driverId = Long.parseLong(json.get("driverId"));

                    // Anti-spoofing: driver can only update trip for themselves
                    if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != driverId) {
                        sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot advance trip status for another driver\"}");
                        return;
                    }

                    String action = json.get("action"); // EN_ROUTE, ARRIVED, START, COMPLETE
                    if (action == null || action.isBlank()) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing action parameter\"}");
                        return;
                    }

                    Trip trip = switch (action.toUpperCase()) {
                        case "EN_ROUTE" -> tripService.driverEnRoute(tripId, driverId);
                        case "ARRIVED" -> tripService.driverArrived(tripId, driverId);
                        case "START" -> tripService.startTrip(tripId, driverId);
                        case "COMPLETE" -> tripService.completeTrip(tripId, driverId,
                                PaymentMethod.valueOf(json.getOrDefault("paymentMethod", "WALLET")));
                        default -> throw new ValidationException("Unknown action: " + action);
                    };

                    sendResponse(exchange, 200, formatTripJson(trip));
                } catch (AuthenticationException e) {
                    sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                User authUser = authenticate(exchange);
                if (authUser.getRole() != UserRole.DRIVER && authUser.getRole() != UserRole.ADMIN) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only drivers can view pending trip requests\"}");
                    return;
                }

                List<Trip> pending = tripService.getPendingBroadcastRequests();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("count", pending.size());
                List<Map<String, Object>> tripList = new ArrayList<>();
                for (Trip t : pending) {
                    tripList.add(formatTripSummary(t));
                }
                body.put("trips", tripList);
                sendResponse(exchange, 200, JsonUtil.toJson(body));
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                User authUser = authenticate(exchange);
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long tripId = Long.parseLong(json.get("tripId"));
                long userId = Long.parseLong(json.get("userId"));
                boolean isPassenger = Boolean.parseBoolean(json.getOrDefault("isPassenger", "true"));

                // Anti-spoofing: cannot cancel on behalf of another user
                if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != userId) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot cancel trip for another user\"}");
                    return;
                }

                Trip trip = tripService.cancelTrip(tripId, userId, isPassenger);
                sendResponse(exchange, 200, formatTripJson(trip));
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                User authUser = authenticate(exchange);
                Map<String, String> json = parseSimpleJson(readBody(exchange));
                long passengerId = Long.parseLong(json.get("passengerId"));

                // Anti-spoofing check
                if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != passengerId) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot top up wallet for another passenger\"}");
                    return;
                }

                BigDecimal amount = new BigDecimal(json.get("amount"));
                String provider = json.getOrDefault("provider", "MOBILE_MONEY");

                BigDecimal newBalance = paymentService.topUpWallet(passengerId, amount, provider);
                String res = String.format(Locale.US, "{\"success\":true,\"passengerId\":%d,\"newBalance\":%.2f}", passengerId, newBalance);
                sendResponse(exchange, 200, res);
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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

                User authUser = authenticate(exchange);
                if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != passengerId) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot view another passenger's wallet balance\"}");
                    return;
                }

                Optional<Passenger> opt = passengerDAO.findById(passengerId);
                if (opt.isEmpty()) {
                    sendResponse(exchange, 404, "{\"error\":\"Passenger not found\"}");
                    return;
                }
                BigDecimal balance = opt.get().getWalletBalance();
                sendResponse(exchange, 200, String.format(Locale.US, "{\"success\":true,\"passengerId\":%d,\"balance\":%.2f}", passengerId, balance));
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                    User authUser = authenticate(exchange);
                    Map<String, String> json = parseSimpleJson(readBody(exchange));
                    long driverId = Long.parseLong(json.get("driverId"));

                    // Anti-spoofing
                    if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != driverId) {
                        sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot update availability for another driver\"}");
                        return;
                    }

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
                    sendResponse(exchange, 200, String.format(Locale.US, "{\"success\":true,\"driverId\":%d,\"status\":\"%s\"}", driverId, availability));
                } catch (AuthenticationException e) {
                    sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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

                    User authUser = authenticate(exchange);
                    if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != driverId) {
                        sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot view availability of another driver\"}");
                        return;
                    }

                    Optional<Driver> opt = driverDAO.findById(driverId);
                    if (opt.isEmpty()) {
                        sendResponse(exchange, 404, "{\"error\":\"Driver not found\"}");
                        return;
                    }
                    sendResponse(exchange, 200, String.format(Locale.US, "{\"success\":true,\"driverId\":%d,\"status\":\"%s\"}", driverId, opt.get().getAvailability()));
                } catch (AuthenticationException e) {
                    sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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

                User authUser = authenticate(exchange);
                if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != driverId) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot view earnings of another driver\"}");
                    return;
                }

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
                sendResponse(exchange, 200, String.format(Locale.US,
                        "{\"success\":true,\"driverId\":%d,\"netEarnings\":%.2f,\"commission\":%.2f,\"grossRevenue\":%.2f,\"completedTrips\":%d,\"ratingAvg\":%.2f,\"availability\":\"%s\"}",
                        driverId, netEarnings, commission, gross, completedCount, driver.getRatingAvg(), driver.getAvailability()));
            } catch (AuthenticationException e) {
                sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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
                    User authUser = authenticate(exchange);
                    Map<String, String> json = parseSimpleJson(readBody(exchange));
                    long tripId = Long.parseLong(json.get("tripId"));
                    long raterId = Long.parseLong(json.get("raterId"));
                    long rateeId = Long.parseLong(json.get("rateeId"));
                    int score = Integer.parseInt(json.get("score"));
                    String comment = json.getOrDefault("comment", "");

                    // Anti-spoofing: caller must be the rater
                    if (authUser.getRole() != UserRole.ADMIN && authUser.getId() != raterId) {
                        sendResponse(exchange, 403, "{\"error\":\"Forbidden: Cannot submit rating on behalf of another user\"}");
                        return;
                    }

                    Rating rating = ratingService.submitRating(tripId, raterId, rateeId, score, comment);
                    sendResponse(exchange, 201, String.format(Locale.US, "{\"success\":true,\"ratingId\":%d,\"score\":%d}",
                            rating.getId(), rating.getScore()));
                } catch (AuthenticationException e) {
                    sendResponse(exchange, 401, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
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

    // --- Authentication & Security Helpers ---

    /**
     * Authenticates an HTTP request via the Authorization Bearer token header or query parameter,
     * validating the token against the MySQL {@code session} table and verifying expiration.
     */
    public User authenticate(HttpExchange exchange) throws AuthenticationException {
        String token = extractBearerToken(exchange);
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("Missing or invalid Authorization header");
        }

        String hash = hashToken(token);
        Optional<Session> optSession = sessionDAO.findByTokenHash(hash);
        if (optSession.isEmpty()) {
            // Check direct token match in case raw token was stored
            optSession = sessionDAO.findByTokenHash(token);
        }

        if (optSession.isEmpty()) {
            throw new AuthenticationException("Invalid session token");
        }

        Session session = optSession.get();
        if (session.isExpired()) {
            sessionDAO.deleteByTokenHash(session.getTokenHash());
            throw new AuthenticationException("Session token has expired");
        }

        Optional<User> optUser = userDAO.findById(session.getUserId());
        if (optUser.isEmpty()) {
            throw new AuthenticationException("User associated with session not found");
        }

        User user = optUser.get();
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthenticationException("Your account has been suspended");
        }

        // Bind user to SessionManager for request thread / token context
        SessionManager.getInstance().login(token, user);
        return user;
    }

    public static String extractBearerToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            String trimmed = authHeader.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        return query.get("token");
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    public static String hashToken(String token) {
        if (token == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
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
     * Safely handles nested objects, arrays, primitives, numbers, booleans, and character escapes.
     */
    public static Map<String, String> parseSimpleJson(String json) {
        return JsonUtil.parseSimpleJson(json);
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

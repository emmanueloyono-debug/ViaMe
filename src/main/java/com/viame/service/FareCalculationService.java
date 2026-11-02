package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.DriverDAO;
import com.viame.dao.PricingRuleDAO;
import com.viame.dao.TransactionManager;
import com.viame.dao.TripDAO;
import com.viame.dao.impl.AuditLogDAOImpl;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.PricingRuleDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.exception.ValidationException;
import com.viame.model.PricingRule;
import com.viame.model.enums.TripStatus;
import com.viame.service.fare.FareStrategy;
import com.viame.service.fare.StandardFareStrategy;
import com.viame.service.fare.SurgeFareStrategy;
import com.viame.util.GeoUtils;
import com.viame.util.InputValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Fare estimation and tariff management (UC03 / UC18, Activity 3 and AD-3).
 */
public class FareCalculationService {

    public static final String CURRENCY = "XAF";
    /** Demand/supply ratio above which the surge strategy is selected. */
    public static final double SURGE_THRESHOLD = 1.5;
    /** Pickup and destination closer than this (km) are considered identical. */
    public static final double MIN_TRIP_DISTANCE_KM = 0.05;

    private static final BigDecimal MIN_SURGE = BigDecimal.ONE;
    private static final BigDecimal MAX_SURGE = new BigDecimal("3.00");

    private final PricingRuleDAO pricingRuleDAO;
    private final TripDAO tripDAO;
    private final DriverDAO driverDAO;
    private final AuditLogDAO auditLogDAO;
    private final TransactionManager transactionManager;

    private final FareStrategy standardStrategy = new StandardFareStrategy();
    private final FareStrategy surgeStrategy = new SurgeFareStrategy();

    public FareCalculationService() {
        this(new PricingRuleDAOImpl(), new TripDAOImpl(), new DriverDAOImpl(), new AuditLogDAOImpl(), new TransactionManager());
    }

    public FareCalculationService(PricingRuleDAO pricingRuleDAO, TripDAO tripDAO, DriverDAO driverDAO,
                                  AuditLogDAO auditLogDAO, TransactionManager transactionManager) {
        this.pricingRuleDAO = pricingRuleDAO;
        this.tripDAO = tripDAO;
        this.driverDAO = driverDAO;
        this.auditLogDAO = auditLogDAO;
        this.transactionManager = transactionManager;
    }

    /**
     * Estimates the fare between two GPS points using the active tariff and live demand.
     *
     * @throws ValidationException if coordinates are invalid or the points are identical
     */
    public FareQuote estimate(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        InputValidator.require(InputValidator.isValidCoordinate(pickupLat, pickupLng), "Invalid pickup coordinates");
        InputValidator.require(InputValidator.isValidCoordinate(dropoffLat, dropoffLng), "Invalid destination coordinates");
        double straightKm = GeoUtils.haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng);
        InputValidator.require(straightKm >= MIN_TRIP_DISTANCE_KM, "Pickup and destination points must be distinct");

        BigDecimal distanceKm = BigDecimal.valueOf(straightKm * GeoUtils.ROAD_DISTANCE_FACTOR)
                .setScale(2, RoundingMode.HALF_UP);
        int durationMin = GeoUtils.estimateDurationMin(distanceKm.doubleValue());
        return quote(distanceKm, durationMin);
    }

    /** Prices a known distance/duration with the active rule and the strategy chosen by demand. */
    public FareQuote quote(BigDecimal distanceKm, int durationMin) {
        PricingRule rule = getActiveRule();
        double ratio = currentDemandSupplyRatio();
        FareStrategy strategy = selectStrategy(ratio);
        return calculate(rule, distanceKm, durationMin, strategy.resolveMultiplier(rule, ratio), strategy.name());
    }

    /**
     * Pure fare formula (no I/O):
     * {@code total = round((base + perKm*distance + perMin*duration) * surge)} in whole XAF.
     */
    public static FareQuote calculate(PricingRule rule, BigDecimal distanceKm, int durationMin,
                                      BigDecimal surgeMultiplier, String strategyName) {
        if (distanceKm == null || distanceKm.signum() <= 0) {
            throw new ValidationException("Distance must be greater than zero");
        }
        if (durationMin < 0) {
            throw new ValidationException("Duration cannot be negative");
        }
        BigDecimal base = rule.getBaseFare().setScale(2, RoundingMode.HALF_UP);
        BigDecimal distanceComponent = rule.getPerKm().multiply(distanceKm).setScale(2, RoundingMode.HALF_UP);
        BigDecimal timeComponent = rule.getPerMin().multiply(BigDecimal.valueOf(durationMin)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal multiplier = surgeMultiplier.setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = base.add(distanceComponent).add(timeComponent)
                .multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP)   // XAF has no subunit
                .setScale(2, RoundingMode.UNNECESSARY);

        return new FareQuote(rule.getId(), distanceKm.setScale(2, RoundingMode.HALF_UP), durationMin,
                base, distanceComponent, timeComponent, multiplier, total, CURRENCY, strategyName);
    }

    /** (pending requests + the one being priced) / online approved drivers. */
    public double currentDemandSupplyRatio() {
        int pending = tripDAO.countByStatus(TripStatus.REQUESTED) + 1;
        int online = driverDAO.countOnlineDrivers();
        return (double) pending / Math.max(online, 1);
    }

    public FareStrategy selectStrategy(double demandSupplyRatio) {
        return demandSupplyRatio > SURGE_THRESHOLD ? surgeStrategy : standardStrategy;
    }

    public PricingRule getActiveRule() {
        return pricingRuleDAO.findActive()
                .orElseThrow(() -> new EntityNotFoundException("No active pricing rule configured"));
    }

    /**
     * Admin tariff change (AD-3): validates, deactivates the current rule, inserts the new
     * rule effective now, and writes an audit entry - all in one transaction. Existing rides
     * keep referencing their original rule.
     */
    public PricingRule updatePricingRule(long adminId, String name, BigDecimal baseFare, BigDecimal perKm,
                                         BigDecimal perMin, BigDecimal surgeMultiplier) {
        String ruleName = InputValidator.requireNonBlank(name, "Tariff name");
        requireNonNegative(baseFare, "Base fare");
        requireNonNegative(perKm, "Per-km rate");
        requireNonNegative(perMin, "Per-minute rate");
        if (surgeMultiplier == null || surgeMultiplier.compareTo(MIN_SURGE) < 0 || surgeMultiplier.compareTo(MAX_SURGE) > 0) {
            throw new ValidationException("Surge multiplier must be between 1.0 and 3.0");
        }

        PricingRule rule = new PricingRule(0, ruleName, baseFare, perKm, perMin, surgeMultiplier, true, LocalDateTime.now());
        return transactionManager.inTransaction(conn -> {
            pricingRuleDAO.deactivateAll(conn);
            pricingRuleDAO.insert(conn, rule);
            auditLogDAO.log(conn, adminId, "PRICING_RULE_CREATE", "pricing_rule", rule.getId());
            return rule;
        });
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new ValidationException(field + " must be zero or positive");
        }
    }
}


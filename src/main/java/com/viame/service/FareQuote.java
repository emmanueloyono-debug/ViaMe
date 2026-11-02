package com.viame.service;

import com.viame.model.Fare;

import java.math.BigDecimal;

/**
 * Immutable fare estimate shown to the passenger before booking and persisted as the
 * ride's {@link Fare} breakdown once the trip is requested.
 *
 * <p>Total = (baseFare + distanceComponent + timeComponent) x surgeMultiplier,
 * rounded to the nearest whole XAF.</p>
 */
public record FareQuote(
        long pricingRuleId,
        BigDecimal distanceKm,
        int durationMin,
        BigDecimal baseFare,
        BigDecimal distanceComponent,
        BigDecimal timeComponent,
        BigDecimal surgeMultiplier,
        BigDecimal totalAmount,
        String currency,
        String strategyName) {

    public Fare toFare(long rideId) {
        return new Fare(0, rideId, baseFare, distanceComponent, timeComponent, surgeMultiplier, totalAmount, currency);
    }
}


package com.viame.service;

import com.viame.model.PricingRule;
import com.viame.model.Trip;
import com.viame.model.enums.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Cancellation Policy Tests")
class CancellationPolicyTest {

    private PricingRule rule;
    private Trip trip;
    private LocalDateTime requestedTime;

    @BeforeEach
    void setUp() {
        rule = new PricingRule(1L, "Standard Day", new BigDecimal("500.00"),
                new BigDecimal("200.00"), new BigDecimal("25.00"), BigDecimal.ONE, true, LocalDateTime.now());
        requestedTime = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
        trip = new Trip(1L, 4L, null, null, 1L, 4.05, 9.76, "Pickup", 4.07, 9.71, "Dropoff",
                new BigDecimal("5.0"), 15, TripStatus.REQUESTED, 1);
        trip.setRequestedAt(requestedTime);
    }

    @Test
    @DisplayName("Passenger cancel while still REQUESTED is free of charge")
    void testPassengerCancelRequestedIsFree() {
        BigDecimal fee = CancellationPolicy.computeFee(trip, true, rule, requestedTime.plusMinutes(5));
        assertEquals(new BigDecimal("0.00"), fee);
    }

    @Test
    @DisplayName("Passenger cancel within 2-minute grace period after driver assigned is free")
    void testPassengerCancelWithinGracePeriodIsFree() {
        trip.transitionTo(TripStatus.ASSIGNED);
        LocalDateTime withinGrace = requestedTime.plusMinutes(1).plusSeconds(30);
        BigDecimal fee = CancellationPolicy.computeFee(trip, true, rule, withinGrace);
        assertEquals(new BigDecimal("0.00"), fee);
    }

    @Test
    @DisplayName("Passenger cancel after 2 minutes incurs base fare fee (500 XAF)")
    void testPassengerCancelAfterGracePeriodChargesFee() {
        trip.transitionTo(TripStatus.ASSIGNED);
        LocalDateTime afterGrace = requestedTime.plusMinutes(3);
        BigDecimal fee = CancellationPolicy.computeFee(trip, true, rule, afterGrace);
        assertEquals(new BigDecimal("500.00"), fee);
    }

    @Test
    @DisplayName("Driver cancel when ARRIVED charges passenger no-show penalty")
    void testDriverCancelArrivedChargesNoShowFee() {
        trip.transitionTo(TripStatus.ASSIGNED);
        trip.transitionTo(TripStatus.DRIVER_EN_ROUTE);
        trip.transitionTo(TripStatus.ARRIVED);

        BigDecimal fee = CancellationPolicy.computeFee(trip, false, rule, requestedTime.plusMinutes(10));
        assertEquals(new BigDecimal("500.00"), fee);
    }
}


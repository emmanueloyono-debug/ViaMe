package com.viame.service;

import com.viame.model.PricingRule;
import com.viame.model.Trip;
import com.viame.model.enums.TripStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pure cancellation-fee rules (no I/O, fully unit-testable).
 *
 * <ul>
 *   <li>Passenger cancels while still REQUESTED (no driver yet) or within 2 minutes of booking: free.</li>
 *   <li>Passenger cancels after a driver is assigned and the 2-minute window has passed: fee = tariff base fare.</li>
 *   <li>Driver cancels because the passenger did not show up (trip ARRIVED): no-show fee = base fare.</li>
 *   <li>Driver cancels in any other state, or emergency cancellation IN_PROGRESS: free for the passenger.</li>
 * </ul>
 */
public final class CancellationPolicy {

    public static final Duration FREE_CANCELLATION_WINDOW = Duration.ofMinutes(2);

    private CancellationPolicy() {
    }

    public static BigDecimal computeFee(Trip trip, boolean cancelledByPassenger, PricingRule rule, LocalDateTime now) {
        BigDecimal fee = rule.getBaseFare().setScale(2, RoundingMode.HALF_UP);
        TripStatus status = trip.getStatus();

        if (status == TripStatus.IN_PROGRESS) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (cancelledByPassenger) {
            if (status == TripStatus.REQUESTED) {
                return BigDecimal.ZERO.setScale(2);
            }
            LocalDateTime requestedAt = trip.getRequestedAt();
            boolean withinWindow = requestedAt != null
                    && !now.isAfter(requestedAt.plus(FREE_CANCELLATION_WINDOW));
            return withinWindow ? BigDecimal.ZERO.setScale(2) : fee;
        }
        // Driver-initiated
        return status == TripStatus.ARRIVED ? fee : BigDecimal.ZERO.setScale(2);
    }
}


package com.viame.service.fare;

import com.viame.model.PricingRule;

import java.math.BigDecimal;

/**
 * Strategy pattern: decides which surge multiplier applies to a fare.
 * Implementations are selected at runtime by {@code FareCalculationService}
 * based on the live demand/supply ratio.
 */
public interface FareStrategy {

    /** Human-readable name shown on the fare quote (e.g. "STANDARD", "SURGE"). */
    String name();

    /**
     * @param rule               the active pricing rule (provides the admin-configured multiplier)
     * @param demandSupplyRatio  pending ride requests / online drivers
     * @return multiplier in the range [1.00, 3.00], scale 2
     */
    BigDecimal resolveMultiplier(PricingRule rule, double demandSupplyRatio);
}


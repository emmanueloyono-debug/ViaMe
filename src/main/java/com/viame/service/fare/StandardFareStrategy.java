package com.viame.service.fare;

import com.viame.model.PricingRule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Normal market conditions: applies only the multiplier configured by the admin on the
 * active pricing rule (1.00 for the standard day tariff).
 */
public class StandardFareStrategy implements FareStrategy {

    static final BigDecimal MIN = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
    static final BigDecimal MAX = new BigDecimal("3.00");

    @Override
    public String name() {
        return "STANDARD";
    }

    @Override
    public BigDecimal resolveMultiplier(PricingRule rule, double demandSupplyRatio) {
        return clamp(rule.getSurgeMultiplier());
    }

    static BigDecimal clamp(BigDecimal value) {
        if (value == null) return MIN;
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(MIN) < 0) return MIN;
        if (scaled.compareTo(MAX) > 0) return MAX;
        return scaled;
    }
}


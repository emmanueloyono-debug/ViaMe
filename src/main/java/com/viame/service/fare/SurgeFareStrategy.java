package com.viame.service.fare;

import com.viame.model.PricingRule;

import java.math.BigDecimal;

/**
 * High-demand conditions (demand/supply ratio &gt; 1.5, spec Activity 3).
 *
 * <p>The demand multiplier scales linearly from 1.3x at ratio 1.5 to 1.8x at ratio 3.0
 * (and stays at 1.8x above). The final multiplier is the higher of this value and the
 * admin-configured rule multiplier, capped at 3.0x.</p>
 */
public class SurgeFareStrategy implements FareStrategy {

    static final double RATIO_LOW = 1.5;
    static final double RATIO_HIGH = 3.0;
    static final double SURGE_LOW = 1.3;
    static final double SURGE_HIGH = 1.8;

    @Override
    public String name() {
        return "SURGE";
    }

    @Override
    public BigDecimal resolveMultiplier(PricingRule rule, double demandSupplyRatio) {
        double t = (demandSupplyRatio - RATIO_LOW) / (RATIO_HIGH - RATIO_LOW);
        t = Math.max(0.0, Math.min(1.0, t));
        BigDecimal demandSurge = BigDecimal.valueOf(SURGE_LOW + t * (SURGE_HIGH - SURGE_LOW));

        BigDecimal ruleSurge = rule.getSurgeMultiplier() != null ? rule.getSurgeMultiplier() : BigDecimal.ONE;
        return StandardFareStrategy.clamp(demandSurge.max(ruleSurge));
    }
}


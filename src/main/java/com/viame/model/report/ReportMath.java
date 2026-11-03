package com.viame.model.report;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure, I/O-free calculations shared by the 5 BI reports (fully unit-testable).
 */
public final class ReportMath {

    public static final BigDecimal PLATFORM_COMMISSION_RATE = new BigDecimal("0.20");

    public static final BigDecimal GOLD_SPEND = new BigDecimal("50000.00");
    public static final BigDecimal SILVER_SPEND = new BigDecimal("15000.00");
    public static final int GOLD_RIDES = 20;
    public static final int SILVER_RIDES = 5;

    private ReportMath() {
    }

    /** 20% platform commission, rounded to 2 decimals. */
    public static BigDecimal commission(BigDecimal gross) {
        return nz(gross).multiply(PLATFORM_COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /** 80% driver payout = gross - commission (always sums back exactly to gross). */
    public static BigDecimal driverPayout(BigDecimal gross) {
        return nz(gross).setScale(2, RoundingMode.HALF_UP).subtract(commission(gross));
    }

    /** part / total as a percentage (0-100, 1 decimal); 0 when total is 0. */
    public static double percentage(double part, double total) {
        if (total <= 0) return 0.0;
        return Math.round(part / total * 1000.0) / 10.0;
    }

    /** Loyalty tier: GOLD (>= 50,000 XAF or >= 20 rides), SILVER (>= 15,000 XAF or >= 5 rides), else BRONZE. */
    public static String loyaltyTier(BigDecimal totalSpend, int completedRides) {
        BigDecimal spend = nz(totalSpend);
        if (spend.compareTo(GOLD_SPEND) >= 0 || completedRides >= GOLD_RIDES) return "GOLD";
        if (spend.compareTo(SILVER_SPEND) >= 0 || completedRides >= SILVER_RIDES) return "SILVER";
        return "BRONZE";
    }

    public static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}


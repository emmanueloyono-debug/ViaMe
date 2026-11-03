package com.viame.model.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ReportMath BI Calculation Tests")
public class ReportMathTest {

    @Test
    @DisplayName("20% commission + 80% payout always sum back to gross")
    public void testCommissionSplit() {
        BigDecimal gross = new BigDecimal("2690.00");
        assertEquals(new BigDecimal("538.00"), ReportMath.commission(gross));
        assertEquals(new BigDecimal("2152.00"), ReportMath.driverPayout(gross));

        BigDecimal odd = new BigDecimal("3575.55");
        assertEquals(odd, ReportMath.commission(odd).add(ReportMath.driverPayout(odd)));
        assertEquals(new BigDecimal("0.00"), ReportMath.commission(null));
    }

    @Test
    @DisplayName("Percentage handles zero totals and rounds to 1 decimal")
    public void testPercentage() {
        assertEquals(0.0, ReportMath.percentage(5, 0));
        assertEquals(66.7, ReportMath.percentage(2, 3));
        assertEquals(100.0, ReportMath.percentage(4, 4));
    }

    @Test
    @DisplayName("Loyalty tiers by spend or ride count")
    public void testLoyaltyTier() {
        assertEquals("GOLD", ReportMath.loyaltyTier(new BigDecimal("50000.00"), 0));
        assertEquals("GOLD", ReportMath.loyaltyTier(BigDecimal.ZERO, 20));
        assertEquals("SILVER", ReportMath.loyaltyTier(new BigDecimal("15000.00"), 1));
        assertEquals("SILVER", ReportMath.loyaltyTier(BigDecimal.ZERO, 5));
        assertEquals("BRONZE", ReportMath.loyaltyTier(new BigDecimal("2690.00"), 1));
        assertEquals("BRONZE", ReportMath.loyaltyTier(null, 0));
    }
}


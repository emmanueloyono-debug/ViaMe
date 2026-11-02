package com.viame.service;

import com.viame.model.PricingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fare Calculation & Pricing Formula Tests")
class FareCalculationServiceTest {

    private final PricingRule standardRule = new PricingRule(1L, "Standard Urban Day Tariff",
            new BigDecimal("500.00"), new BigDecimal("200.00"), new BigDecimal("25.00"),
            BigDecimal.ONE, true, LocalDateTime.now());

    @Test
    @DisplayName("Calculates exact standard tariff: Base 500 + 8.2km*200 + 22min*25 = 2690 XAF")
    void testStandardFareCalculation() {
        // Base = 500, Distance = 8.2 * 200 = 1640, Time = 22 * 25 = 550, Total = 2690 * 1.0 = 2690.00
        FareQuote quote = FareCalculationService.calculate(
                standardRule, new BigDecimal("8.20"), 22, BigDecimal.ONE, "STANDARD");

        assertEquals(new BigDecimal("500.00"), quote.baseFare());
        assertEquals(new BigDecimal("1640.00"), quote.distanceComponent());
        assertEquals(new BigDecimal("550.00"), quote.timeComponent());
        assertEquals(new BigDecimal("1.00"), quote.surgeMultiplier());
        assertEquals(new BigDecimal("2690.00"), quote.totalAmount());
        assertEquals("XAF", quote.currency());
    }

    @Test
    @DisplayName("Applies surge multiplier correctly: 1.4x surge on 2690 XAF = 3766.00 XAF")
    void testSurgeFareCalculation() {
        // (500 + 1640 + 550) * 1.4 = 2690 * 1.4 = 3766.00
        FareQuote quote = FareCalculationService.calculate(
                standardRule, new BigDecimal("8.20"), 22, new BigDecimal("1.40"), "SURGE");

        assertEquals(new BigDecimal("3766.00"), quote.totalAmount());
        assertEquals(new BigDecimal("1.40"), quote.surgeMultiplier());
    }

    @Test
    @DisplayName("Invalid distance or duration throws ValidationException")
    void testInvalidInputsThrow() {
        assertThrows(RuntimeException.class, () ->
                FareCalculationService.calculate(standardRule, BigDecimal.ZERO, 10, BigDecimal.ONE, "STANDARD"));
        assertThrows(RuntimeException.class, () ->
                FareCalculationService.calculate(standardRule, new BigDecimal("-5.00"), 10, BigDecimal.ONE, "STANDARD"));
        assertThrows(RuntimeException.class, () ->
                FareCalculationService.calculate(standardRule, new BigDecimal("5.00"), -1, BigDecimal.ONE, "STANDARD"));
    }
}


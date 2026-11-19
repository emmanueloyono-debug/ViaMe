package com.viame.dao;

import com.viame.dao.impl.PricingRuleDAOImpl;
import com.viame.model.PricingRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PricingRuleDAO Database Integration Tests")
class PricingRuleDAOTest {

    private PricingRuleDAO pricingRuleDAO;
    private PricingRule testRule;

    @BeforeEach
    void setUp() {
        pricingRuleDAO = new PricingRuleDAOImpl();
    }

    @AfterEach
    void tearDown() {
        if (testRule != null && testRule.getId() > 0) {
            try {
                pricingRuleDAO.delete(testRule.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Find seed pricing rules and active rule")
    void testFindSeedRules() {
        // Seed rule 1: Standard Urban Day Tariff
        Optional<PricingRule> rule1 = pricingRuleDAO.findById(1L);
        assertTrue(rule1.isPresent());
        assertEquals("Standard Urban Day Tariff", rule1.get().getName());
        assertEquals(0, new BigDecimal("500.00").compareTo(rule1.get().getBaseFare()));
        assertEquals(0, new BigDecimal("200.00").compareTo(rule1.get().getPerKm()));
        assertEquals(0, new BigDecimal("25.00").compareTo(rule1.get().getPerMin()));

        // Active rule
        Optional<PricingRule> active = pricingRuleDAO.findActive();
        assertTrue(active.isPresent());
        assertTrue(active.get().isActive());

        // Find all rules
        List<PricingRule> all = pricingRuleDAO.findAll();
        assertNotNull(all);
        assertTrue(all.size() >= 2);
    }

    @Test
    @DisplayName("Save, update, and delete custom pricing rule")
    void testPricingRuleLifecycle() {
        testRule = new PricingRule(0, "Test Midnight Tariff",
                new BigDecimal("800.00"), new BigDecimal("300.00"),
                new BigDecimal("40.00"), new BigDecimal("1.50"),
                false, LocalDateTime.of(2026, 12, 1, 0, 0));

        PricingRule saved = pricingRuleDAO.save(testRule);
        assertNotNull(saved);
        assertTrue(saved.getId() > 0);

        Optional<PricingRule> fetched = pricingRuleDAO.findById(saved.getId());
        assertTrue(fetched.isPresent());
        assertEquals("Test Midnight Tariff", fetched.get().getName());
        assertEquals(0, new BigDecimal("800.00").compareTo(fetched.get().getBaseFare()));
        assertEquals(0, new BigDecimal("1.50").compareTo(fetched.get().getSurgeMultiplier()));
        assertFalse(fetched.get().isActive());

        // Update
        saved.setName("Updated Midnight Tariff");
        saved.setBaseFare(new BigDecimal("850.00"));
        boolean updated = pricingRuleDAO.update(saved);
        assertTrue(updated);

        PricingRule reFetched = pricingRuleDAO.findById(saved.getId()).orElseThrow();
        assertEquals("Updated Midnight Tariff", reFetched.getName());
        assertEquals(0, new BigDecimal("850.00").compareTo(reFetched.getBaseFare()));

        // Delete
        boolean deleted = pricingRuleDAO.delete(saved.getId());
        assertTrue(deleted);
        assertTrue(pricingRuleDAO.findById(saved.getId()).isEmpty());
        testRule = null;
    }
}

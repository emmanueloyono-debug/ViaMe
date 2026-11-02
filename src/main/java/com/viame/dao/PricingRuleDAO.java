package com.viame.dao;

import com.viame.model.PricingRule;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface PricingRuleDAO extends GenericDAO<PricingRule, Long> {
    /** The single tariff currently applied to new rides. */
    Optional<PricingRule> findActive();
    void deactivateAll(Connection conn) throws SQLException;
    PricingRule insert(Connection conn, PricingRule rule) throws SQLException;
}


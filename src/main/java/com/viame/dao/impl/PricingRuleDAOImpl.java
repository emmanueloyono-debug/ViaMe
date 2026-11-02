package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.PricingRuleDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.PricingRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PricingRuleDAOImpl implements PricingRuleDAO {

    @Override
    public Optional<PricingRule> findById(Long id) {
        return findOne("SELECT * FROM pricing_rule WHERE id = ?", id);
    }

    @Override
    public Optional<PricingRule> findActive() {
        return findOne("SELECT * FROM pricing_rule WHERE active = TRUE ORDER BY valid_from DESC, id DESC LIMIT 1", null);
    }

    @Override
    public List<PricingRule> findAll() {
        List<PricingRule> rules = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM pricing_rule ORDER BY valid_from DESC");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rules.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listing pricing rules", e);
        }
        return rules;
    }

    @Override
    public PricingRule save(PricingRule rule) {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, rule);
        } catch (SQLException e) {
            throw new DatabaseException("Error saving pricing rule", e);
        }
    }

    @Override
    public PricingRule insert(Connection conn, PricingRule rule) throws SQLException {
        String sql = "INSERT INTO pricing_rule (name, base_fare, per_km, per_min, surge_multiplier, active, valid_from) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, rule.getName());
            stmt.setBigDecimal(2, rule.getBaseFare());
            stmt.setBigDecimal(3, rule.getPerKm());
            stmt.setBigDecimal(4, rule.getPerMin());
            stmt.setBigDecimal(5, rule.getSurgeMultiplier());
            stmt.setBoolean(6, rule.isActive());
            stmt.setTimestamp(7, Timestamp.valueOf(rule.getValidFrom()));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    rule.setId(keys.getLong(1));
                }
            }
            return rule;
        }
    }

    @Override
    public void deactivateAll(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE pricing_rule SET active = FALSE WHERE active = TRUE")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Pricing rules are versioned history (rides reference them); prefer creating a new rule
     * through {@code FareCalculationService.updatePricingRule} instead of editing in place.
     */
    @Override
    public boolean update(PricingRule rule) {
        String sql = "UPDATE pricing_rule SET name = ?, base_fare = ?, per_km = ?, per_min = ?, surge_multiplier = ?, active = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rule.getName());
            stmt.setBigDecimal(2, rule.getBaseFare());
            stmt.setBigDecimal(3, rule.getPerKm());
            stmt.setBigDecimal(4, rule.getPerMin());
            stmt.setBigDecimal(5, rule.getSurgeMultiplier());
            stmt.setBoolean(6, rule.isActive());
            stmt.setLong(7, rule.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error updating pricing rule: " + rule.getId(), e);
        }
    }

    @Override
    public boolean delete(Long id) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM pricing_rule WHERE id = ?")) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error deleting pricing rule (it may be referenced by rides): " + id, e);
        }
    }

    private Optional<PricingRule> findOne(String sql, Long param) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (param != null) {
                stmt.setLong(1, param);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error retrieving pricing rule", e);
        }
    }

    private PricingRule map(ResultSet rs) throws SQLException {
        Timestamp validFrom = rs.getTimestamp("valid_from");
        return new PricingRule(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("base_fare"),
                rs.getBigDecimal("per_km"),
                rs.getBigDecimal("per_min"),
                rs.getBigDecimal("surge_multiplier"),
                rs.getBoolean("active"),
                validFrom != null ? validFrom.toLocalDateTime() : null);
    }
}


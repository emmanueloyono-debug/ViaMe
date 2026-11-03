package com.viame.dao.impl;

import com.viame.dao.DBConnection;
import com.viame.dao.ReportDAO;
import com.viame.exception.DatabaseException;
import com.viame.model.report.ReportMath;
import com.viame.model.report.ReportRows.CancellationRow;
import com.viame.model.report.ReportRows.CustomerLoyaltyRow;
import com.viame.model.report.ReportRows.DailyRevenueRow;
import com.viame.model.report.ReportRows.DriverPerformanceRow;
import com.viame.model.report.ReportRows.PeakHourRow;
import com.viame.model.report.ReportRows.PickupZoneRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of the 5 BI aggregate reports. All queries are parameter-bound.
 */
public class ReportDAOImpl implements ReportDAO {

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Report 1: completed rides grouped by completion date. */
    @Override
    public List<DailyRevenueRow> dailyRevenue(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT DATE(r.completed_at) AS day, COUNT(*) AS trips, COALESCE(SUM(f.total_amount), 0) AS gross
            FROM ride r
            JOIN fare f ON f.ride_id = r.id
            WHERE r.status = 'COMPLETED' AND r.completed_at >= ? AND r.completed_at < ?
            GROUP BY DATE(r.completed_at)
            ORDER BY day
            """;
        return query(sql, from, to, rs -> {
            BigDecimal gross = rs.getBigDecimal("gross");
            return new DailyRevenueRow(rs.getDate("day").toLocalDate(), rs.getInt("trips"),
                    gross.setScale(2, RoundingMode.HALF_UP), ReportMath.commission(gross), ReportMath.driverPayout(gross));
        });
    }

    /** Report 2: per-driver volume, cancellations, completion rate and rating. */
    @Override
    public List<DriverPerformanceRow> driverPerformance(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT d.user_id, d.full_name, d.rating_avg,
                   COUNT(r.id) AS assigned,
                   COALESCE(SUM(r.status = 'COMPLETED'), 0) AS completed,
                   COALESCE(SUM(r.status = 'CANCELLED'), 0) AS cancelled,
                   COALESCE(SUM(CASE WHEN r.status = 'COMPLETED' THEN f.total_amount END), 0) AS gross
            FROM driver_profile d
            LEFT JOIN ride r ON r.driver_id = d.user_id AND r.requested_at >= ? AND r.requested_at < ?
            LEFT JOIN fare f ON f.ride_id = r.id
            GROUP BY d.user_id, d.full_name, d.rating_avg
            ORDER BY completed DESC, d.rating_avg DESC
            """;
        return query(sql, from, to, rs -> {
            int assigned = rs.getInt("assigned");
            int completed = rs.getInt("completed");
            return new DriverPerformanceRow(rs.getLong("user_id"), rs.getString("full_name"), assigned, completed,
                    rs.getInt("cancelled"), ReportMath.percentage(completed, assigned),
                    rs.getBigDecimal("rating_avg"), rs.getBigDecimal("gross").setScale(2, RoundingMode.HALF_UP));
        });
    }

    /** Report 3a: request volume and average surge per hour of day. */
    @Override
    public List<PeakHourRow> peakHours(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT HOUR(r.requested_at) AS hr, COUNT(*) AS requests,
                   COALESCE(SUM(r.status = 'COMPLETED'), 0) AS completed,
                   COALESCE(AVG(f.surge_multiplier), 1.00) AS avg_surge
            FROM ride r
            LEFT JOIN fare f ON f.ride_id = r.id
            WHERE r.requested_at >= ? AND r.requested_at < ?
            GROUP BY HOUR(r.requested_at)
            ORDER BY hr
            """;
        return query(sql, from, to, rs -> new PeakHourRow(rs.getInt("hr"), rs.getInt("requests"),
                rs.getInt("completed"), rs.getBigDecimal("avg_surge").setScale(2, RoundingMode.HALF_UP)));
    }

    /** Report 3b: busiest pickup zones. */
    @Override
    public List<PickupZoneRow> topPickupZones(LocalDateTime from, LocalDateTime to, int limit) {
        String sql = """
            SELECT r.pickup_address AS zone, COUNT(*) AS requests,
                   COALESCE(AVG(f.surge_multiplier), 1.00) AS avg_surge
            FROM ride r
            LEFT JOIN fare f ON f.ride_id = r.id
            WHERE r.requested_at >= ? AND r.requested_at < ?
            GROUP BY r.pickup_address
            ORDER BY requests DESC, zone
            LIMIT ?
            """;
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from));
            stmt.setTimestamp(2, Timestamp.valueOf(to));
            stmt.setInt(3, Math.max(1, limit));
            List<PickupZoneRow> rows = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PickupZoneRow(rs.getString("zone"), rs.getInt("requests"),
                            rs.getBigDecimal("avg_surge").setScale(2, RoundingMode.HALF_UP)));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new DatabaseException("Error generating pickup zone report", e);
        }
    }

    /**
     * Report 4: cancellations grouped by who cancelled. The cancelling party is resolved
     * from the TRIP_CANCELLED audit entry (written by TripService) joined to the actor's role.
     */
    @Override
    public List<CancellationRow> cancellations(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT COALESCE(u.role, 'SYSTEM') AS party,
                   COUNT(DISTINCT r.id) AS cancelled,
                   COALESCE(AVG(TIMESTAMPDIFF(SECOND, r.requested_at, r.cancelled_at)), 0) AS avg_wait_sec,
                   COALESCE(SUM(f.total_amount), 0) AS lost
            FROM ride r
            LEFT JOIN fare f ON f.ride_id = r.id
            LEFT JOIN audit_log a ON a.entity = 'ride' AND a.entity_id = r.id AND a.action = 'TRIP_CANCELLED'
            LEFT JOIN `user` u ON u.id = a.user_id
            WHERE r.status = 'CANCELLED' AND r.requested_at >= ? AND r.requested_at < ?
            GROUP BY COALESCE(u.role, 'SYSTEM')
            ORDER BY cancelled DESC
            """;
        return query(sql, from, to, rs -> new CancellationRow(rs.getString("party"), rs.getInt("cancelled"),
                Math.round(rs.getDouble("avg_wait_sec") / 60.0 * 10.0) / 10.0,
                rs.getBigDecimal("lost").setScale(2, RoundingMode.HALF_UP)));
    }

    /** Report 5: passenger spend, wallet vs cash split and loyalty tier. */
    @Override
    public List<CustomerLoyaltyRow> customerLoyalty(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT p.user_id, p.full_name,
                   COUNT(r.id) AS rides,
                   COALESCE(SUM(f.total_amount), 0) AS spend,
                   COALESCE(SUM(CASE WHEN pay.method = 'WALLET' THEN pay.amount END), 0) AS wallet_amt,
                   COALESCE(SUM(CASE WHEN pay.method = 'CASH' THEN pay.amount END), 0) AS cash_amt,
                   COALESCE(SUM(pay.amount), 0) AS paid_amt
            FROM passenger_profile p
            LEFT JOIN ride r ON r.passenger_id = p.user_id AND r.status = 'COMPLETED'
                            AND r.completed_at >= ? AND r.completed_at < ?
            LEFT JOIN fare f ON f.ride_id = r.id
            LEFT JOIN payment pay ON pay.ride_id = r.id AND pay.status = 'PAID'
            GROUP BY p.user_id, p.full_name
            ORDER BY spend DESC, rides DESC
            """;
        return query(sql, from, to, rs -> {
            BigDecimal spend = rs.getBigDecimal("spend").setScale(2, RoundingMode.HALF_UP);
            int rides = rs.getInt("rides");
            double paid = rs.getDouble("paid_amt");
            return new CustomerLoyaltyRow(rs.getLong("user_id"), rs.getString("full_name"), rides, spend,
                    ReportMath.percentage(rs.getDouble("wallet_amt"), paid),
                    ReportMath.percentage(rs.getDouble("cash_amt"), paid),
                    ReportMath.loyaltyTier(spend, rides));
        });
    }

    private <T> List<T> query(String sql, LocalDateTime from, LocalDateTime to, RowMapper<T> mapper) {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from));
            stmt.setTimestamp(2, Timestamp.valueOf(to));
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new DatabaseException("Error generating report", e);
        }
    }
}


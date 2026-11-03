package com.viame.dao;

import com.viame.model.report.ReportRows.CancellationRow;
import com.viame.model.report.ReportRows.CustomerLoyaltyRow;
import com.viame.model.report.ReportRows.DailyRevenueRow;
import com.viame.model.report.ReportRows.DriverPerformanceRow;
import com.viame.model.report.ReportRows.PeakHourRow;
import com.viame.model.report.ReportRows.PickupZoneRow;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Analytical aggregate queries for the 5 BI reports.
 * All ranges are half-open: {@code from <= t < to}.
 */
public interface ReportDAO {
    List<DailyRevenueRow> dailyRevenue(LocalDateTime from, LocalDateTime to);
    List<DriverPerformanceRow> driverPerformance(LocalDateTime from, LocalDateTime to);
    List<PeakHourRow> peakHours(LocalDateTime from, LocalDateTime to);
    List<PickupZoneRow> topPickupZones(LocalDateTime from, LocalDateTime to, int limit);
    List<CancellationRow> cancellations(LocalDateTime from, LocalDateTime to);
    List<CustomerLoyaltyRow> customerLoyalty(LocalDateTime from, LocalDateTime to);
}


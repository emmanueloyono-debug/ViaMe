package com.viame.model.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable row types for the 5 Business Intelligence reports (spec section 7).
 */
public final class ReportRows {

    private ReportRows() {
    }

    /** Report 1: Daily Revenue &amp; Commission Ledger. */
    public record DailyRevenueRow(LocalDate date, int tripsCompleted, BigDecimal grossRevenue,
                                  BigDecimal platformCommission, BigDecimal driverPayouts) {
    }

    /** Report 2: Driver Performance &amp; Evaluation Matrix. */
    public record DriverPerformanceRow(long driverId, String driverName, int tripsAssigned, int tripsCompleted,
                                       int cancellations, double completionRatePct, BigDecimal averageRating,
                                       BigDecimal grossRevenue) {
    }

    /** Report 3a: Peak Hour demand (0-23h). */
    public record PeakHourRow(int hourOfDay, int totalRequests, int completed, BigDecimal averageSurge) {
    }

    /** Report 3b: High-demand pickup zones. */
    public record PickupZoneRow(String pickupZone, int totalRequests, BigDecimal averageSurge) {
    }

    /** Report 4: Trip Cancellation &amp; Attrition Analysis (grouped by cancelling party). */
    public record CancellationRow(String cancelledBy, int totalCancelled, double avgMinutesBeforeCancel,
                                  BigDecimal lostRevenue) {
    }

    /** Report 5: Customer Loyalty &amp; Spending Segmentation. */
    public record CustomerLoyaltyRow(long passengerId, String passengerName, int completedRides, BigDecimal totalSpend,
                                     double walletUsagePct, double cashUsagePct, String tier) {
    }
}


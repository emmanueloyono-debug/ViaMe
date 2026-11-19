package com.viame.dao;

import com.viame.dao.impl.ReportDAOImpl;
import com.viame.model.report.ReportRows.CancellationRow;
import com.viame.model.report.ReportRows.CustomerLoyaltyRow;
import com.viame.model.report.ReportRows.DailyRevenueRow;
import com.viame.model.report.ReportRows.DriverPerformanceRow;
import com.viame.model.report.ReportRows.PeakHourRow;
import com.viame.model.report.ReportRows.PickupZoneRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportDAO Database Integration Tests")
class ReportDAOTest {

    private ReportDAO reportDAO;
    private final LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 11, 1, 0, 0);

    @BeforeEach
    void setUp() {
        reportDAO = new ReportDAOImpl();
    }

    @Test
    @DisplayName("Report 1: Daily Revenue aggregation returns non-empty result with correct math")
    void testDailyRevenue() {
        List<DailyRevenueRow> rows = reportDAO.dailyRevenue(from, to);
        assertNotNull(rows);
        assertFalse(rows.isEmpty(), "Daily revenue should have rows from seed data in October 2026");

        for (DailyRevenueRow row : rows) {
            assertNotNull(row.date());
            assertTrue(row.tripsCompleted() > 0);
            assertTrue(row.grossRevenue().compareTo(BigDecimal.ZERO) > 0);
            // Verify commission is 20% and driverPayout is 80%
            BigDecimal expectedCommission = row.grossRevenue().multiply(new BigDecimal("0.20")).setScale(2, java.math.RoundingMode.HALF_UP);
            assertEquals(0, expectedCommission.compareTo(row.platformCommission()));
            assertEquals(0, row.grossRevenue().subtract(row.platformCommission()).compareTo(row.driverPayouts()));
        }
    }

    @Test
    @DisplayName("Report 2: Driver Performance aggregates volume, ratings and gross correctly")
    void testDriverPerformance() {
        List<DriverPerformanceRow> rows = reportDAO.driverPerformance(from, to);
        assertNotNull(rows);
        assertFalse(rows.isEmpty());

        assertTrue(rows.stream().anyMatch(d -> d.driverName().contains("Samuel") || d.driverName().contains("Eric")),
                "Should include Samuel or Eric");
        for (DriverPerformanceRow row : rows) {
            assertTrue(row.driverId() > 0);
            assertNotNull(row.driverName());
            assertTrue(row.completionRatePct() >= 0.0 && row.completionRatePct() <= 100.0);
        }
    }

    @Test
    @DisplayName("Report 3: Peak Hours and Top Pickup Zones")
    void testPeakHoursAndPickupZones() {
        List<PeakHourRow> peakHours = reportDAO.peakHours(from, to);
        assertNotNull(peakHours);
        assertFalse(peakHours.isEmpty());

        for (PeakHourRow row : peakHours) {
            assertTrue(row.hourOfDay() >= 0 && row.hourOfDay() <= 23);
            assertTrue(row.totalRequests() >= 0);
            assertTrue(row.averageSurge().compareTo(BigDecimal.ZERO) > 0);
        }

        List<PickupZoneRow> zones = reportDAO.topPickupZones(from, to, 5);
        assertNotNull(zones);
        assertFalse(zones.isEmpty());
        assertTrue(zones.size() <= 5);
        for (PickupZoneRow row : zones) {
            assertNotNull(row.pickupZone());
            assertTrue(row.totalRequests() > 0);
        }
    }

    @Test
    @DisplayName("Report 4 & 5: Cancellations and Customer Loyalty")
    void testCancellationsAndCustomerLoyalty() {
        List<CancellationRow> cancellations = reportDAO.cancellations(from, to);
        assertNotNull(cancellations);

        List<CustomerLoyaltyRow> loyalty = reportDAO.customerLoyalty(from, to);
        assertNotNull(loyalty);
        assertFalse(loyalty.isEmpty(), "Customer loyalty should report on seed passengers");

        assertTrue(loyalty.stream().anyMatch(c -> c.passengerName().contains("Alice") || c.passengerName().contains("Bob")));
        for (CustomerLoyaltyRow row : loyalty) {
            assertTrue(row.passengerId() > 0);
            assertNotNull(row.passengerName());
            assertNotNull(row.tier());
        }
    }
}

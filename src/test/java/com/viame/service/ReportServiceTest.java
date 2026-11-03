package com.viame.service;

import com.viame.dao.ReportDAO;
import com.viame.exception.ValidationException;
import com.viame.model.report.ReportRows.CancellationRow;
import com.viame.model.report.ReportRows.CustomerLoyaltyRow;
import com.viame.model.report.ReportRows.DailyRevenueRow;
import com.viame.model.report.ReportRows.DriverPerformanceRow;
import com.viame.model.report.ReportRows.PeakHourRow;
import com.viame.model.report.ReportRows.PickupZoneRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportService Range, Delegation & CSV Export Tests")
public class ReportServiceTest {

    /** Fake DAO capturing the computed half-open range. */
    public static class CapturingDAO implements ReportDAO {
        public LocalDateTime from;
        public LocalDateTime to;
        public int limit;

        @Override
        public List<DailyRevenueRow> dailyRevenue(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
            return List.of(new DailyRevenueRow(LocalDate.of(2026, 10, 25), 2,
                    new BigDecimal("6265.00"), new BigDecimal("1253.00"), new BigDecimal("5012.00")));
        }

        @Override
        public List<DriverPerformanceRow> driverPerformance(LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<PeakHourRow> peakHours(LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<PickupZoneRow> topPickupZones(LocalDateTime from, LocalDateTime to, int limit) {
            this.limit = limit;
            return List.of(new PickupZoneRow("Akwa Palace Hotel, Douala", 3, new BigDecimal("1.00")));
        }

        @Override
        public List<CancellationRow> cancellations(LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<CustomerLoyaltyRow> customerLoyalty(LocalDateTime from, LocalDateTime to) {
            return List.of();
        }
    }

    @Test
    @DisplayName("Inclusive date range is converted to half-open [from 00:00, to+1 00:00)")
    public void testRangeConversion() {
        CapturingDAO dao = new CapturingDAO();
        new ReportService(dao).dailyRevenue(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), dao.from);
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), dao.to);
    }

    @Test
    @DisplayName("Invalid ranges are rejected")
    public void testInvalidRanges() {
        ReportService service = new ReportService(new CapturingDAO());
        LocalDate d = LocalDate.of(2026, 10, 10);
        assertThrows(ValidationException.class, () -> service.dailyRevenue(null, d));
        assertThrows(ValidationException.class, () -> service.dailyRevenue(d, d.minusDays(1)));
        assertThrows(ValidationException.class, () -> service.dailyRevenue(d, d.plusDays(400)));
    }

    @Test
    @DisplayName("Top pickup zones defaults the limit to 10")
    public void testDefaultLimit() {
        CapturingDAO dao = new CapturingDAO();
        new ReportService(dao).topPickupZones(LocalDate.now(), LocalDate.now(), 0);
        assertEquals(ReportService.DEFAULT_TOP_ZONES, dao.limit);
    }

    @Test
    @DisplayName("CSV export writes header + rows and escapes commas")
    public void testCsvExport() {
        CapturingDAO dao = new CapturingDAO();
        ReportService service = new ReportService(dao);

        String revenueCsv = ReportService.toCsv(
                service.dailyRevenue(LocalDate.now(), LocalDate.now()), DailyRevenueRow.class);
        assertTrue(revenueCsv.startsWith("date,tripsCompleted,grossRevenue,platformCommission,driverPayouts\r\n"));
        assertTrue(revenueCsv.contains("2026-10-25,2,6265.00,1253.00,5012.00"));

        String zonesCsv = ReportService.toCsv(
                service.topPickupZones(LocalDate.now(), LocalDate.now(), 5), PickupZoneRow.class);
        assertTrue(zonesCsv.contains("\"Akwa Palace Hotel, Douala\",3,1.00"));
    }
}


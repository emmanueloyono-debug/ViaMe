package com.viame.service;

import com.viame.dao.ReportDAO;
import com.viame.dao.impl.ReportDAOImpl;
import com.viame.exception.ValidationException;
import com.viame.model.report.ReportRows.CancellationRow;
import com.viame.model.report.ReportRows.CustomerLoyaltyRow;
import com.viame.model.report.ReportRows.DailyRevenueRow;
import com.viame.model.report.ReportRows.DriverPerformanceRow;
import com.viame.model.report.ReportRows.PeakHourRow;
import com.viame.model.report.ReportRows.PickupZoneRow;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business Intelligence report service (UC10 / UC20, spec section 7).
 *
 * <p>Accepts inclusive calendar date ranges, delegates aggregation to {@link ReportDAO},
 * and offers generic CSV export for any report row type.</p>
 */
public class ReportService {

    public static final int MAX_RANGE_DAYS = 366;
    public static final int DEFAULT_TOP_ZONES = 10;

    private final ReportDAO reportDAO;

    public ReportService() {
        this(new ReportDAOImpl());
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    /** Report 1: Daily Revenue &amp; Commission Ledger. */
    public List<DailyRevenueRow> dailyRevenue(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return reportDAO.dailyRevenue(start(from), endExclusive(to));
    }

    /** Report 2: Driver Performance &amp; Evaluation Matrix. */
    public List<DriverPerformanceRow> driverPerformance(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return reportDAO.driverPerformance(start(from), endExclusive(to));
    }

    /** Report 3a: Peak Hour demand. */
    public List<PeakHourRow> peakHours(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return reportDAO.peakHours(start(from), endExclusive(to));
    }

    /** Report 3b: High-demand pickup zones. */
    public List<PickupZoneRow> topPickupZones(LocalDate from, LocalDate to, int limit) {
        validateRange(from, to);
        return reportDAO.topPickupZones(start(from), endExclusive(to), limit > 0 ? limit : DEFAULT_TOP_ZONES);
    }

    /** Report 4: Trip Cancellation &amp; Attrition Analysis. */
    public List<CancellationRow> cancellations(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return reportDAO.cancellations(start(from), endExclusive(to));
    }

    /** Report 5: Customer Loyalty &amp; Spending Segmentation. */
    public List<CustomerLoyaltyRow> customerLoyalty(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return reportDAO.customerLoyalty(start(from), endExclusive(to));
    }

    /**
     * Renders any list of report records as RFC-4180 CSV (header from record component names).
     */
    public static String toCsv(List<? extends Record> rows, Class<? extends Record> type) {
        RecordComponent[] components = type.getRecordComponents();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(components[i].getName());
        }
        sb.append("\r\n");
        for (Record row : rows) {
            for (int i = 0; i < components.length; i++) {
                if (i > 0) sb.append(',');
                try {
                    Object value = components[i].getAccessor().invoke(row);
                    sb.append(escapeCsv(value == null ? "" : value.toString()));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read report field " + components[i].getName(), e);
                }
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public static Path exportCsv(List<? extends Record> rows, Class<? extends Record> type, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.writeString(target, toCsv(rows, type), StandardCharsets.UTF_8);
    }

    static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("Report start and end dates are required");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("Report end date must be on or after the start date");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ValidationException("Report range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    private static LocalDateTime start(LocalDate from) {
        return from.atStartOfDay();
    }

    private static LocalDateTime endExclusive(LocalDate to) {
        return to.plusDays(1).atStartOfDay();
    }
}


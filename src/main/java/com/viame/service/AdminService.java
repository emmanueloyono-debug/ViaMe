package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.DriverDAO;
import com.viame.dao.TransactionManager;
import com.viame.dao.TripDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.AuditLogDAOImpl;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.model.Driver;
import com.viame.model.PricingRule;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.VehicleStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dispatcher &amp; Administrator Backend Service (AD-2 Driver Verification, AD-3 Tariff Config, Fleet Monitoring).
 * Coordinates fleet queries, transactional onboarding approvals/rejections, and pricing rule updates.
 */
public class AdminService {

    private static final List<TripStatus> LIVE_TRIP_STATUSES = List.of(
            TripStatus.REQUESTED,
            TripStatus.ASSIGNED,
            TripStatus.DRIVER_EN_ROUTE,
            TripStatus.ARRIVED,
            TripStatus.IN_PROGRESS
    );

    private static final int AUDIT_ACTION_MAX_LENGTH = 60;

    private final DriverDAO driverDAO;
    private final VehicleDAO vehicleDAO;
    private final TripDAO tripDAO;
    private final AuditLogDAO auditLogDAO;
    private final FareCalculationService fareCalculationService;
    private final TransactionManager transactionManager;

    public AdminService() {
        this(new DriverDAOImpl(), new VehicleDAOImpl(), new TripDAOImpl(),
                new AuditLogDAOImpl(), new FareCalculationService(), new TransactionManager());
    }

    public AdminService(DriverDAO driverDAO, VehicleDAO vehicleDAO, TripDAO tripDAO,
                        AuditLogDAO auditLogDAO, FareCalculationService fareCalculationService,
                        TransactionManager transactionManager) {
        this.driverDAO = driverDAO;
        this.vehicleDAO = vehicleDAO;
        this.tripDAO = tripDAO;
        this.auditLogDAO = auditLogDAO;
        this.fareCalculationService = fareCalculationService;
        this.transactionManager = transactionManager;
    }

    /**
     * Returns all active (non-terminal) trips across REQUESTED, ASSIGNED, DRIVER_EN_ROUTE,
     * ARRIVED, and IN_PROGRESS statuses, ordered newest first.
     */
    public List<Trip> getLiveFleetTrips() {
        List<Trip> liveTrips = new ArrayList<>();
        for (TripStatus status : LIVE_TRIP_STATUSES) {
            List<Trip> byStatus = tripDAO.findByStatus(status);
            if (byStatus != null) {
                liveTrips.addAll(byStatus);
            }
        }
        liveTrips.sort(Comparator.comparing(Trip::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Trip::getId, Comparator.reverseOrder()));
        return liveTrips;
    }

    public List<Trip> getAllTrips() {
        return tripDAO.findAll();
    }

    public List<Driver> getPendingVerificationDrivers() {
        return driverDAO.findByVerificationStatus(DriverVerificationStatus.PENDING);
    }

    public Optional<Vehicle> getVehicleForDriver(long driverId) {
        return vehicleDAO.findByDriverId(driverId);
    }

    public int countOnlineDrivers() {
        return driverDAO.countOnlineDrivers();
    }

    /**
     * Approves a pending driver and activates their registered vehicle inside one ACID transaction (AD-2).
     */
    public boolean approveDriver(long adminId, long driverId) {
        return transactionManager.inTransaction(conn -> {
            if (driverDAO.findById(driverId).isEmpty()) {
                throw new EntityNotFoundException("Driver", driverId);
            }
            boolean updated = driverDAO.updateVerificationStatus(conn, driverId, DriverVerificationStatus.APPROVED);
            vehicleDAO.updateStatusByDriver(conn, driverId, VehicleStatus.ACTIVE);
            auditLogDAO.log(conn, adminId, "DRIVER_APPROVED", "driver_profile", driverId);
            return updated;
        });
    }

    /**
     * Rejects a pending driver and deactivates their vehicle inside one ACID transaction (AD-2).
     */
    public boolean rejectDriver(long adminId, long driverId, String reason) {
        return transactionManager.inTransaction(conn -> {
            if (driverDAO.findById(driverId).isEmpty()) {
                throw new EntityNotFoundException("Driver", driverId);
            }
            boolean updated = driverDAO.updateVerificationStatus(conn, driverId, DriverVerificationStatus.REJECTED);
            vehicleDAO.updateStatusByDriver(conn, driverId, VehicleStatus.INACTIVE);
            String action = "DRIVER_REJECTED" + (reason != null && !reason.isBlank() ? ":" + reason.trim() : "");
            if (action.length() > AUDIT_ACTION_MAX_LENGTH) {
                action = action.substring(0, AUDIT_ACTION_MAX_LENGTH);
            }
            auditLogDAO.log(conn, adminId, action, "driver_profile", driverId);
            return updated;
        });
    }

    public PricingRule getActivePricingRule() {
        return fareCalculationService.getActiveRule();
    }

    public PricingRule updateTariff(long adminId, String name, BigDecimal baseFare, BigDecimal perKm,
                                    BigDecimal perMin, BigDecimal surgeMultiplier) {
        return fareCalculationService.updatePricingRule(adminId, name, baseFare, perKm, perMin, surgeMultiplier);
    }
}

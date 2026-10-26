package com.viame.model;

import com.viame.exception.IllegalStateTransitionException;
import com.viame.model.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core transactional lifecycle entity representing a ride in ViaMe.
 * Incorporates state machine verification and optimistic locking version counter.
 */
public class Trip {
    private long id;
    private long passengerId;
    private Long driverId;
    private Long vehicleId;
    private long pricingRuleId;
    private double pickupLat;
    private double pickupLng;
    private String pickupAddress;
    private double dropoffLat;
    private double dropoffLng;
    private String dropoffAddress;
    private BigDecimal distanceKm;
    private int durationMin;
    private TripStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private int version;

    public Trip(long id, long passengerId, Long driverId, Long vehicleId, long pricingRuleId,
                double pickupLat, double pickupLng, String pickupAddress,
                double dropoffLat, double dropoffLng, String dropoffAddress,
                BigDecimal distanceKm, int durationMin, TripStatus status, int version) {
        this.id = id;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.pricingRuleId = pricingRuleId;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.pickupAddress = pickupAddress;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.dropoffAddress = dropoffAddress;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
        this.status = status != null ? status : TripStatus.REQUESTED;
        this.version = version;
        this.requestedAt = LocalDateTime.now();
    }

    /**
     * Validates and transitions ride status according to the formal state machine.
     * Throws IllegalStateTransitionException on invalid progression.
     */
    public void transitionTo(TripStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new IllegalStateTransitionException("Illegal trip status transition from " 
                    + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case ASSIGNED -> this.acceptedAt = now;
            case IN_PROGRESS -> this.startedAt = now;
            case COMPLETED -> this.completedAt = now;
            case CANCELLED -> this.cancelledAt = now;
            default -> {}
        }
    }

    public static boolean isValidTransition(TripStatus from, TripStatus to) {
        if (from == null || to == null) return false;
        return switch (from) {
            case REQUESTED -> to == TripStatus.ASSIGNED || to == TripStatus.NO_DRIVER_FOUND || to == TripStatus.CANCELLED;
            case ASSIGNED -> to == TripStatus.DRIVER_EN_ROUTE || to == TripStatus.REQUESTED || to == TripStatus.CANCELLED;
            case DRIVER_EN_ROUTE -> to == TripStatus.ARRIVED || to == TripStatus.CANCELLED;
            case ARRIVED -> to == TripStatus.IN_PROGRESS || to == TripStatus.CANCELLED;
            case IN_PROGRESS -> to == TripStatus.COMPLETED || to == TripStatus.CANCELLED;
            case COMPLETED, CANCELLED, NO_DRIVER_FOUND -> false; // Terminal states
        };
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPassengerId() { return passengerId; }
    public void setPassengerId(long passengerId) { this.passengerId = passengerId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public long getPricingRuleId() { return pricingRuleId; }
    public void setPricingRuleId(long pricingRuleId) { this.pricingRuleId = pricingRuleId; }

    public double getPickupLat() { return pickupLat; }
    public void setPickupLat(double pickupLat) { this.pickupLat = pickupLat; }

    public double getPickupLng() { return pickupLng; }
    public void setPickupLng(double pickupLng) { this.pickupLng = pickupLng; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public double getDropoffLat() { return dropoffLat; }
    public void setDropoffLat(double dropoffLat) { this.dropoffLat = dropoffLat; }

    public double getDropoffLng() { return dropoffLng; }
    public void setDropoffLng(double dropoffLng) { this.dropoffLng = dropoffLng; }

    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }

    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}

package com.viame.model;

import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;

import java.math.BigDecimal;

/**
 * Concrete subtype representing a Driver in ViaMe.
 * Encapsulates driver licensing, verification, live GPS coordinates, and earnings.
 */
public class Driver extends User {
    private String fullName;
    private String licenceNumber;
    private DriverVerificationStatus verificationStatus;
    private BigDecimal ratingAvg;
    private DriverAvailability availability;
    private double currentLat;
    private double currentLng;
    private BigDecimal totalEarnings;

    public Driver(long id, String email, String phone, String passwordHash, String passwordSalt,
                  UserStatus status, String fullName, String licenceNumber,
                  DriverVerificationStatus verificationStatus, BigDecimal ratingAvg,
                  DriverAvailability availability, double currentLat, double currentLng,
                  BigDecimal totalEarnings) {
        super(id, email, phone, passwordHash, passwordSalt, UserRole.DRIVER, status);
        this.fullName = fullName;
        this.licenceNumber = licenceNumber;
        this.verificationStatus = verificationStatus != null ? verificationStatus : DriverVerificationStatus.PENDING;
        this.ratingAvg = ratingAvg != null ? ratingAvg : BigDecimal.valueOf(5.00);
        this.availability = availability != null ? availability : DriverAvailability.OFFLINE;
        this.currentLat = currentLat;
        this.currentLng = currentLng;
        this.totalEarnings = totalEarnings != null ? totalEarnings : BigDecimal.ZERO;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getLicenceNumber() { return licenceNumber; }
    public void setLicenceNumber(String licenceNumber) { this.licenceNumber = licenceNumber; }

    public DriverVerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(DriverVerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public BigDecimal getRatingAvg() { return ratingAvg; }
    public void setRatingAvg(BigDecimal ratingAvg) { this.ratingAvg = ratingAvg; }

    public DriverAvailability getAvailability() { return availability; }
    public void setAvailability(DriverAvailability availability) { this.availability = availability; }

    public double getCurrentLat() { return currentLat; }
    public void setCurrentLat(double currentLat) { this.currentLat = currentLat; }

    public double getCurrentLng() { return currentLng; }
    public void setCurrentLng(double currentLng) { this.currentLng = currentLng; }

    public BigDecimal getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(BigDecimal totalEarnings) { this.totalEarnings = totalEarnings; }

    public void creditEarnings(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.totalEarnings = this.totalEarnings.add(amount);
    }
}

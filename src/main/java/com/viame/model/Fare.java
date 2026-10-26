package com.viame.model;

import java.math.BigDecimal;

public class Fare {
    private long id;
    private long rideId;
    private BigDecimal baseFare;
    private BigDecimal distanceComponent;
    private BigDecimal timeComponent;
    private BigDecimal surgeMultiplier;
    private BigDecimal totalAmount;
    private String currency;

    public Fare(long id, long rideId, BigDecimal baseFare, BigDecimal distanceComponent,
                BigDecimal timeComponent, BigDecimal surgeMultiplier, BigDecimal totalAmount, String currency) {
        this.id = id;
        this.rideId = rideId;
        this.baseFare = baseFare;
        this.distanceComponent = distanceComponent;
        this.timeComponent = timeComponent;
        this.surgeMultiplier = surgeMultiplier != null ? surgeMultiplier : BigDecimal.ONE;
        this.totalAmount = totalAmount;
        this.currency = currency != null ? currency : "XAF";
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRideId() { return rideId; }
    public void setRideId(long rideId) { this.rideId = rideId; }

    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }

    public BigDecimal getDistanceComponent() { return distanceComponent; }
    public void setDistanceComponent(BigDecimal distanceComponent) { this.distanceComponent = distanceComponent; }

    public BigDecimal getTimeComponent() { return timeComponent; }
    public void setTimeComponent(BigDecimal timeComponent) { this.timeComponent = timeComponent; }

    public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
    public void setSurgeMultiplier(BigDecimal surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

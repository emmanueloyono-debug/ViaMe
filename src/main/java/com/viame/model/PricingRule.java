package com.viame.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PricingRule {
    private long id;
    private String name;
    private BigDecimal baseFare;
    private BigDecimal perKm;
    private BigDecimal perMin;
    private BigDecimal surgeMultiplier;
    private boolean active;
    private LocalDateTime validFrom;

    public PricingRule(long id, String name, BigDecimal baseFare, BigDecimal perKm,
                       BigDecimal perMin, BigDecimal surgeMultiplier, boolean active, LocalDateTime validFrom) {
        this.id = id;
        this.name = name;
        this.baseFare = baseFare;
        this.perKm = perKm;
        this.perMin = perMin;
        this.surgeMultiplier = surgeMultiplier != null ? surgeMultiplier : BigDecimal.ONE;
        this.active = active;
        this.validFrom = validFrom != null ? validFrom : LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }

    public BigDecimal getPerKm() { return perKm; }
    public void setPerKm(BigDecimal perKm) { this.perKm = perKm; }

    public BigDecimal getPerMin() { return perMin; }
    public void setPerMin(BigDecimal perMin) { this.perMin = perMin; }

    public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
    public void setSurgeMultiplier(BigDecimal surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
}

package com.viame.model;

import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Payment {
    private long id;
    private long rideId;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String transactionRef;
    private LocalDateTime paidAt;

    public Payment(long id, long rideId, BigDecimal amount, PaymentMethod method,
                   PaymentStatus status, String transactionRef, LocalDateTime paidAt) {
        this.id = id;
        this.rideId = rideId;
        this.amount = amount;
        this.method = method;
        this.status = status != null ? status : PaymentStatus.PENDING;
        this.transactionRef = transactionRef;
        this.paidAt = paidAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRideId() { return rideId; }
    public void setRideId(long rideId) { this.rideId = rideId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}

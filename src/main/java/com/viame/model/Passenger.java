package com.viame.model;

import com.viame.exception.InsufficientFundsException;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserRole;
import com.viame.model.enums.UserStatus;

import java.math.BigDecimal;

/**
 * Concrete subtype representing a Passenger in ViaMe.
 * Encapsulates passenger profile attributes and wallet balance operations.
 */
public class Passenger extends User {
    private String fullName;
    private PaymentMethod defaultPaymentMethod;
    private BigDecimal walletBalance;

    public Passenger(long id, String email, String phone, String passwordHash, String passwordSalt,
                     UserStatus status, String fullName, PaymentMethod defaultPaymentMethod, BigDecimal walletBalance) {
        super(id, email, phone, passwordHash, passwordSalt, UserRole.PASSENGER, status);
        this.fullName = fullName;
        this.defaultPaymentMethod = defaultPaymentMethod != null ? defaultPaymentMethod : PaymentMethod.CASH;
        this.walletBalance = walletBalance != null ? walletBalance : BigDecimal.ZERO;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public PaymentMethod getDefaultPaymentMethod() { return defaultPaymentMethod; }
    public void setDefaultPaymentMethod(PaymentMethod defaultPaymentMethod) { this.defaultPaymentMethod = defaultPaymentMethod; }

    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }

    public void creditWallet(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be strictly positive");
        }
        this.walletBalance = this.walletBalance.add(amount);
    }

    public void debitWallet(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }
        if (this.walletBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient wallet funds: Available " 
                    + this.walletBalance + " XAF, Required " + amount + " XAF");
        }
        this.walletBalance = this.walletBalance.subtract(amount);
    }
}

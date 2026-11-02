package com.viame.model;

import com.viame.exception.InsufficientFundsException;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Passenger Domain Model Tests")
class PassengerTest {

    private Passenger passenger;

    @BeforeEach
    void setUp() {
        passenger = new Passenger(1L, "alice@viame.cm", "+237670000004", "hash", "salt",
                UserStatus.ACTIVE, "Alice Mengue", PaymentMethod.WALLET, new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("creditWallet increases balance correctly for positive amount")
    void testCreditWalletValid() {
        passenger.creditWallet(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("15000.00"), passenger.getWalletBalance());
    }

    @Test
    @DisplayName("creditWallet throws IllegalArgumentException on zero or negative amount")
    void testCreditWalletInvalid() {
        assertThrows(IllegalArgumentException.class, () -> passenger.creditWallet(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> passenger.creditWallet(new BigDecimal("-100.00")));
        assertThrows(IllegalArgumentException.class, () -> passenger.creditWallet(null));
    }

    @Test
    @DisplayName("debitWallet deducts funds when balance is sufficient")
    void testDebitWalletValid() {
        passenger.debitWallet(new BigDecimal("3500.00"));
        assertEquals(new BigDecimal("6500.00"), passenger.getWalletBalance());
    }

    @Test
    @DisplayName("debitWallet throws InsufficientFundsException on overdraft")
    void testDebitWalletOverdraft() {
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> passenger.debitWallet(new BigDecimal("20000.00")));
        assertTrue(ex.getMessage().contains("Insufficient wallet funds"));
    }

    @Test
    @DisplayName("debitWallet throws IllegalArgumentException on zero or negative amount")
    void testDebitWalletInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () -> passenger.debitWallet(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> passenger.debitWallet(new BigDecimal("-500.00")));
    }
}


package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.DriverDAO;
import com.viame.dao.FareDAO;
import com.viame.dao.PassengerDAO;
import com.viame.dao.PaymentDAO;
import com.viame.dao.TransactionManager;
import com.viame.dao.TripDAO;
import com.viame.dao.impl.AuditLogDAOImpl;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.FareDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.PaymentDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Fare;
import com.viame.model.Payment;
import com.viame.model.Trip;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.PaymentStatus;
import com.viame.model.enums.TripStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ACID Payment Settlement & Digital Wallet Service (UC07 / UC09).
 * Manages atomic fare debits, driver payouts, platform commission (20%), and wallet top-ups.
 */
public class PaymentService {

    public static final BigDecimal PLATFORM_COMMISSION_RATE = new BigDecimal("0.20");
    public static final BigDecimal MIN_WALLET_TOPUP = new BigDecimal("500.00");

    private final PaymentDAO paymentDAO;
    private final FareDAO fareDAO;
    private final PassengerDAO passengerDAO;
    private final DriverDAO driverDAO;
    private final TripDAO tripDAO;
    private final AuditLogDAO auditLogDAO;
    private final TransactionManager transactionManager;

    public PaymentService() {
        this(new PaymentDAOImpl(), new FareDAOImpl(), new PassengerDAOImpl(),
             new DriverDAOImpl(), new TripDAOImpl(), new AuditLogDAOImpl(), new TransactionManager());
    }

    public PaymentService(PaymentDAO paymentDAO, FareDAO fareDAO, PassengerDAO passengerDAO,
                          DriverDAO driverDAO, TripDAO tripDAO, AuditLogDAO auditLogDAO,
                          TransactionManager transactionManager) {
        this.paymentDAO = paymentDAO;
        this.fareDAO = fareDAO;
        this.passengerDAO = passengerDAO;
        this.driverDAO = driverDAO;
        this.tripDAO = tripDAO;
        this.auditLogDAO = auditLogDAO;
        this.transactionManager = transactionManager;
    }

    /**
     * Executes atomic payment settlement for a completed ride (UC07).
     * Calculates 20% platform commission and 80% driver net earnings.
     *
     * @param tripId  the target ride identifier
     * @param method  settlement method (WALLET, CASH, MOBILE_MONEY, CARD)
     * @return Settled Payment entity
     */
    public Payment settleTripPayment(long tripId, PaymentMethod method) {
        Trip trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        Fare fare = fareDAO.findByRideId(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Fare breakdown not found for trip", tripId));

        BigDecimal grossAmount = fare.getTotalAmount();
        BigDecimal platformCommission = grossAmount.multiply(PLATFORM_COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal driverNet = grossAmount.subtract(platformCommission);

        String transactionRef = "TXN-" + System.currentTimeMillis() + "-" + tripId;

        return transactionManager.inTransaction(conn -> {
            // Check for duplicate payment settlement
            Optional<Payment> existing = paymentDAO.findByRideIdForUpdate(conn, tripId);
            if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.PAID) {
                return existing.get();
            }

            if (method == PaymentMethod.WALLET) {
                boolean debited = passengerDAO.debitWallet(conn, trip.getPassengerId(), grossAmount);
                if (!debited) {
                    BigDecimal balance = passengerDAO.getWalletBalance(conn, trip.getPassengerId());
                    throw new InsufficientFundsException("Insufficient wallet funds: Available "
                            + balance + " XAF, Required " + grossAmount + " XAF");
                }
            }

            // Credit driver's accumulated net earnings if a driver was assigned
            if (trip.getDriverId() != null) {
                driverDAO.creditEarnings(conn, trip.getDriverId(), driverNet);
            }

            Payment payment = new Payment(0, tripId, grossAmount, method, PaymentStatus.PAID,
                    transactionRef, LocalDateTime.now());
            paymentDAO.insert(conn, payment);

            // Ensure trip status is transitioned to COMPLETED if not already
            if (trip.getStatus() != TripStatus.COMPLETED) {
                trip.transitionTo(TripStatus.COMPLETED);
                tripDAO.update(conn, trip);
            }

            auditLogDAO.log(conn, trip.getPassengerId(), "PAYMENT_SETTLED", "payment", payment.getId());
            return payment;
        });
    }

    /**
     * Tops up a passenger's digital wallet balance (UC09).
     *
     * @param passengerId passenger identifier
     * @param amount      deposit amount (minimum 500 XAF)
     * @param provider    payment gateway or simulation provider
     * @return updated wallet balance
     */
    public BigDecimal topUpWallet(long passengerId, BigDecimal amount, String provider) {
        if (amount == null || amount.compareTo(MIN_WALLET_TOPUP) < 0) {
            throw new ValidationException("Minimum wallet top-up amount is " + MIN_WALLET_TOPUP + " XAF");
        }

        return transactionManager.inTransaction(conn -> {
            passengerDAO.creditWallet(conn, passengerId, amount);
            BigDecimal newBalance = passengerDAO.getWalletBalance(conn, passengerId);
            auditLogDAO.log(conn, passengerId, "WALLET_TOPUP_" + (provider != null ? provider : "SIMULATED"),
                    "passenger_profile", passengerId);
            return newBalance;
        });
    }

    /**
     * Processes cancellation penalty fee when applicable.
     */
    public Optional<Payment> processCancellationFee(long tripId, long passengerId, Long driverId, BigDecimal fee) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        String transactionRef = "CANC-FEE-" + System.currentTimeMillis() + "-" + tripId;
        BigDecimal driverCompensation = fee.multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);

        return Optional.of(transactionManager.inTransaction(conn -> {
            boolean debited = passengerDAO.debitWallet(conn, passengerId, fee);
            PaymentStatus status = debited ? PaymentStatus.PAID : PaymentStatus.FAILED;

            if (debited && driverId != null) {
                driverDAO.creditEarnings(conn, driverId, driverCompensation);
            }

            Payment payment = new Payment(0, tripId, fee, PaymentMethod.WALLET, status,
                    transactionRef, debited ? LocalDateTime.now() : null);
            paymentDAO.insert(conn, payment);
            auditLogDAO.log(conn, passengerId, "CANCELLATION_FEE_" + status, "payment", payment.getId());
            return payment;
        }));
    }

    public Optional<Payment> getPaymentForTrip(long tripId) {
        return paymentDAO.findByRideId(tripId);
    }
}


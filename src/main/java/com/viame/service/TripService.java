package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.DriverDAO;
import com.viame.dao.FareDAO;
import com.viame.dao.PassengerDAO;
import com.viame.dao.PricingRuleDAO;
import com.viame.dao.TransactionManager;
import com.viame.dao.TripDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.AuditLogDAOImpl;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.FareDAOImpl;
import com.viame.dao.impl.PassengerDAOImpl;
import com.viame.dao.impl.PricingRuleDAOImpl;
import com.viame.dao.impl.TripDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.exception.IllegalStateTransitionException;
import com.viame.exception.InsufficientFundsException;
import com.viame.exception.ValidationException;
import com.viame.model.Fare;
import com.viame.model.PricingRule;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.VehicleStatus;
import com.viame.util.InputValidator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core Ride Lifecycle & Dispatch Orchestration Service (UC04, UC05, UC06, AD-1, State Machine).
 * Governs ride request creation, pessimistic lock assignment, lifecycle transitions, and cancellations.
 */
public class TripService {

    private final TripDAO tripDAO;
    private final FareDAO fareDAO;
    private final PassengerDAO passengerDAO;
    private final DriverDAO driverDAO;
    private final VehicleDAO vehicleDAO;
    private final PricingRuleDAO pricingRuleDAO;
    private final FareCalculationService fareCalculationService;
    private final PaymentService paymentService;
    private final AuditLogDAO auditLogDAO;
    private final TransactionManager transactionManager;

    public TripService() {
        this(new TripDAOImpl(), new FareDAOImpl(), new PassengerDAOImpl(),
             new DriverDAOImpl(), new VehicleDAOImpl(), new PricingRuleDAOImpl(),
             new FareCalculationService(), new PaymentService(),
             new AuditLogDAOImpl(), new TransactionManager());
    }

    public TripService(TripDAO tripDAO, FareDAO fareDAO, PassengerDAO passengerDAO,
                       DriverDAO driverDAO, VehicleDAO vehicleDAO, PricingRuleDAO pricingRuleDAO,
                       FareCalculationService fareCalculationService, PaymentService paymentService,
                       AuditLogDAO auditLogDAO, TransactionManager transactionManager) {
        this.tripDAO = tripDAO;
        this.fareDAO = fareDAO;
        this.passengerDAO = passengerDAO;
        this.driverDAO = driverDAO;
        this.vehicleDAO = vehicleDAO;
        this.pricingRuleDAO = pricingRuleDAO;
        this.fareCalculationService = fareCalculationService;
        this.paymentService = paymentService;
        this.auditLogDAO = auditLogDAO;
        this.transactionManager = transactionManager;
    }

    /**
     * Creates a new ride booking request for a passenger (UC04).
     */
    public Trip requestTrip(long passengerId, double pickupLat, double pickupLng, String pickupAddress,
                            double dropoffLat, double dropoffLng, String dropoffAddress,
                            PaymentMethod paymentMethod) {
        String pAddress = InputValidator.requireNonBlank(pickupAddress, "Pickup address");
        String dAddress = InputValidator.requireNonBlank(dropoffAddress, "Dropoff address");

        // Verify passenger does not already have an active uncompleted trip
        Optional<Trip> active = tripDAO.findActiveByPassengerId(passengerId);
        if (active.isPresent()) {
            throw new ValidationException("You already have an active trip in progress (Trip #" + active.get().getId() + ")");
        }

        FareQuote quote = fareCalculationService.estimate(pickupLat, pickupLng, dropoffLat, dropoffLng);

        // If paying via wallet, verify sufficient initial funds upfront
        if (paymentMethod == PaymentMethod.WALLET) {
            transactionManager.inTransaction(conn -> {
                BigDecimal balance = passengerDAO.getWalletBalance(conn, passengerId);
                if (balance.compareTo(quote.totalAmount()) < 0) {
                    throw new InsufficientFundsException("Insufficient wallet balance: Available "
                            + balance + " XAF, Estimated fare " + quote.totalAmount() + " XAF");
                }
                return null;
            });
        }

        Trip trip = new Trip(0, passengerId, null, null, quote.pricingRuleId(),
                pickupLat, pickupLng, pAddress, dropoffLat, dropoffLng, dAddress,
                quote.distanceKm(), quote.durationMin(), TripStatus.REQUESTED, 1);

        return transactionManager.inTransaction(conn -> {
            tripDAO.insert(conn, trip);
            Fare fare = quote.toFare(trip.getId());
            fareDAO.insert(conn, fare);
            auditLogDAO.log(conn, passengerId, "TRIP_REQUESTED", "ride", trip.getId());
            return trip;
        });
    }

    /**
     * Accepts and locks a ride request for a driver using pessimistic locking (UC05).
     * Prevents race conditions when multiple drivers attempt to accept the same trip.
     */
    public Trip acceptTrip(long tripId, long driverId) {
        Vehicle vehicle = vehicleDAO.findByDriverId(driverId)
                .orElseThrow(() -> new ValidationException("Driver does not have an assigned vehicle"));

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new ValidationException("Vehicle status is not ACTIVE (" + vehicle.getStatus() + ")");
        }

        boolean assigned = tripDAO.lockAndAssignTrip(tripId, driverId, vehicle.getId());
        if (!assigned) {
            throw new IllegalStateTransitionException("Trip #" + tripId + " is no longer available or was already accepted");
        }

        auditLogDAO.log(driverId, "TRIP_ASSIGNED", "ride", tripId);
        return tripDAO.findById(tripId).orElseThrow();
    }

    /**
     * Driver indicates they are en route to pickup location.
     */
    public Trip driverEnRoute(long tripId, long driverId) {
        return transitionState(tripId, driverId, TripStatus.DRIVER_EN_ROUTE);
    }

    /**
     * Driver arrives at pickup location.
     */
    public Trip driverArrived(long tripId, long driverId) {
        return transitionState(tripId, driverId, TripStatus.ARRIVED);
    }

    /**
     * Driver starts the trip once passenger has boarded.
     */
    public Trip startTrip(long tripId, long driverId) {
        return transitionState(tripId, driverId, TripStatus.IN_PROGRESS);
    }

    /**
     * Completes the trip and initiates automated payment settlement (UC06 / UC07).
     */
    public Trip completeTrip(long tripId, long driverId, PaymentMethod paymentMethod) {
        Trip trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getDriverId() == null || trip.getDriverId() != driverId) {
            throw new ValidationException("Driver is not assigned to this trip");
        }

        trip.transitionTo(TripStatus.COMPLETED);

        transactionManager.inTransaction(conn -> {
            tripDAO.update(conn, trip);
            driverDAO.updateAvailability(conn, driverId, DriverAvailability.ONLINE);
            auditLogDAO.log(conn, driverId, "TRIP_COMPLETED", "ride", tripId);
            return null;
        });

        // Trigger payment settlement
        paymentService.settleTripPayment(tripId, paymentMethod != null ? paymentMethod : PaymentMethod.CASH);
        return trip;
    }

    /**
     * Cancels a ride request, applying cancellation policy fees if applicable.
     */
    public Trip cancelTrip(long tripId, long actorUserId, boolean isPassenger) {
        Trip trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        PricingRule rule = pricingRuleDAO.findById(trip.getPricingRuleId())
                .orElseGet(() -> fareCalculationService.getActiveRule());

        LocalDateTime now = LocalDateTime.now();
        BigDecimal cancellationFee = CancellationPolicy.computeFee(trip, isPassenger, rule, now);

        trip.transitionTo(TripStatus.CANCELLED);

        transactionManager.inTransaction(conn -> {
            tripDAO.update(conn, trip);
            if (trip.getDriverId() != null) {
                driverDAO.updateAvailability(conn, trip.getDriverId(), DriverAvailability.ONLINE);
            }
            auditLogDAO.log(conn, actorUserId, "TRIP_CANCELLED", "ride", tripId);
            return null;
        });

        if (cancellationFee.compareTo(BigDecimal.ZERO) > 0) {
            paymentService.processCancellationFee(tripId, trip.getPassengerId(), trip.getDriverId(), cancellationFee);
        }

        return trip;
    }

    private Trip transitionState(long tripId, long driverId, TripStatus targetStatus) {
        Trip trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getDriverId() == null || trip.getDriverId() != driverId) {
            throw new ValidationException("Driver is not assigned to this trip");
        }

        trip.transitionTo(targetStatus);
        boolean updated = tripDAO.update(trip);
        if (!updated) {
            throw new IllegalStateTransitionException("Concurrent update conflict on trip #" + tripId);
        }

        auditLogDAO.log(driverId, "TRIP_STATUS_" + targetStatus.name(), "ride", tripId);
        return trip;
    }

    public Optional<Trip> getTripById(long tripId) {
        return tripDAO.findById(tripId);
    }

    public Optional<Trip> getActiveTripForPassenger(long passengerId) {
        return tripDAO.findActiveByPassengerId(passengerId);
    }

    public List<Trip> getTripsForPassenger(long passengerId) {
        return tripDAO.findByPassengerId(passengerId);
    }

    public List<Trip> getTripsForDriver(long driverId) {
        return tripDAO.findByDriverId(driverId);
    }

    public List<Trip> getPendingBroadcastRequests() {
        return tripDAO.findByStatus(TripStatus.REQUESTED);
    }
}


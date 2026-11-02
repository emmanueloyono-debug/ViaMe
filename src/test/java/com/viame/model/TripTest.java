package com.viame.model;

import com.viame.exception.IllegalStateTransitionException;
import com.viame.model.enums.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trip State Machine Tests")
class TripTest {

    private Trip trip;

    @BeforeEach
    void setUp() {
        trip = new Trip(10L, 4L, null, null, 1L,
                4.051056, 9.767869, "Akwa Palace Hotel",
                4.075833, 9.718611, "Bonamoussadi Market",
                new BigDecimal("8.20"), 22, TripStatus.REQUESTED, 1);
    }

    @Test
    @DisplayName("Full happy path ride lifecycle state transitions")
    void testHappyPathTransitions() {
        assertEquals(TripStatus.REQUESTED, trip.getStatus());

        trip.transitionTo(TripStatus.ASSIGNED);
        assertEquals(TripStatus.ASSIGNED, trip.getStatus());
        assertNotNull(trip.getAcceptedAt());

        trip.transitionTo(TripStatus.DRIVER_EN_ROUTE);
        assertEquals(TripStatus.DRIVER_EN_ROUTE, trip.getStatus());

        trip.transitionTo(TripStatus.ARRIVED);
        assertEquals(TripStatus.ARRIVED, trip.getStatus());

        trip.transitionTo(TripStatus.IN_PROGRESS);
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
        assertNotNull(trip.getStartedAt());

        trip.transitionTo(TripStatus.COMPLETED);
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertNotNull(trip.getCompletedAt());
    }

    @Test
    @DisplayName("Cancellation is allowed from intermediate states")
    void testCancellationTransitions() {
        trip.transitionTo(TripStatus.CANCELLED);
        assertEquals(TripStatus.CANCELLED, trip.getStatus());
        assertNotNull(trip.getCancelledAt());
    }

    @Test
    @DisplayName("Invalid transition throws IllegalStateTransitionException")
    void testInvalidTransitionThrows() {
        assertThrows(IllegalStateTransitionException.class, () -> trip.transitionTo(TripStatus.COMPLETED));
        assertThrows(IllegalStateTransitionException.class, () -> trip.transitionTo(TripStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("Terminal states cannot transition anywhere")
    void testTerminalStatesCannotTransition() {
        trip.transitionTo(TripStatus.ASSIGNED);
        trip.transitionTo(TripStatus.DRIVER_EN_ROUTE);
        trip.transitionTo(TripStatus.ARRIVED);
        trip.transitionTo(TripStatus.IN_PROGRESS);
        trip.transitionTo(TripStatus.COMPLETED);

        assertThrows(IllegalStateTransitionException.class, () -> trip.transitionTo(TripStatus.CANCELLED));
        assertThrows(IllegalStateTransitionException.class, () -> trip.transitionTo(TripStatus.REQUESTED));
    }
}


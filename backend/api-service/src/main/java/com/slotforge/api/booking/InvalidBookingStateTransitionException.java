package com.slotforge.api.booking;

import java.util.UUID;

public class InvalidBookingStateTransitionException
        extends RuntimeException {

    public InvalidBookingStateTransitionException(
            UUID bookingId,
            BookingStatus fromState,
            BookingStatus toState
    ) {
        super(
                "Booking %s cannot transition from %s to %s"
                        .formatted(bookingId, fromState, toState)
        );
    }
}

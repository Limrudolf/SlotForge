package com.slotforge.api.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class BookingStateMachineTests {

    private static final Instant EXPIRY =
            Instant.parse("2026-08-20T12:15:00Z");

    @Test
    void authorizesAndConfirmsPendingBooking() {
        Booking booking = newBooking();

        booking.authorizePayment();
        assertEquals(
                BookingStatus.PAYMENT_AUTHORIZED,
                booking.getStatus()
        );

        booking.confirm();
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void failsPendingBooking() {
        Booking booking = newBooking();

        booking.failPayment();

        assertEquals(BookingStatus.PAYMENT_FAILED, booking.getStatus());
    }

    @Test
    void expiresPendingBooking() {
        Booking booking = newBooking();

        booking.expire();

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }

    @Test
    void rejectsFailureAfterConfirmation() {
        Booking booking = newBooking();
        booking.authorizePayment();
        booking.confirm();

        assertThrows(
                InvalidBookingStateTransitionException.class,
                booking::failPayment
        );
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void rejectsDuplicateFailure() {
        Booking booking = newBooking();
        booking.failPayment();

        assertThrows(
                InvalidBookingStateTransitionException.class,
                booking::failPayment
        );
        assertEquals(BookingStatus.PAYMENT_FAILED, booking.getStatus());
    }

    @Test
    void rejectsConfirmationWithoutAuthorization() {
        Booking booking = newBooking();

        assertThrows(
                InvalidBookingStateTransitionException.class,
                booking::confirm
        );
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
    }

    @Test
    void paymentHoldIsActiveBeforeDeadline() {
        assertFalse(newBooking().isPaymentExpired(EXPIRY.minusNanos(1)));
    }

    @Test
    void paymentHoldExpiresAtExactDeadline() {
        assertTrue(newBooking().isPaymentExpired(EXPIRY));
    }

    private static Booking newBooking() {
        return new Booking(null, null, EXPIRY);
    }
}

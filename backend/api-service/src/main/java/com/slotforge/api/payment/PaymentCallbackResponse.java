package com.slotforge.api.payment;

import java.util.UUID;

import com.slotforge.api.booking.BookingStatus;

public record PaymentCallbackResponse(
        UUID paymentIntentId,
        PaymentIntentStatus paymentStatus,
        UUID bookingId,
        BookingStatus bookingStatus,
        boolean replayed
) {

    public static PaymentCallbackResponse from(
            PaymentIntent paymentIntent,
            boolean replayed
    ) {
        return new PaymentCallbackResponse(
                paymentIntent.getId(),
                paymentIntent.getStatus(),
                paymentIntent.getBooking().getId(),
                paymentIntent.getBooking().getStatus(),
                replayed
        );
    }
}

package com.slotforge.api.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID id,
        UUID bookingId,
        long amountMinor,
        String currency,
        PaymentIntentStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentIntentResponse from(PaymentIntent paymentIntent) {
        return new PaymentIntentResponse(
                paymentIntent.getId(),
                paymentIntent.getBooking().getId(),
                paymentIntent.getAmountMinor(),
                paymentIntent.getCurrency().getCurrencyCode(),
                paymentIntent.getStatus(),
                paymentIntent.getCreatedAt(),
                paymentIntent.getUpdatedAt()
        );
    }
}

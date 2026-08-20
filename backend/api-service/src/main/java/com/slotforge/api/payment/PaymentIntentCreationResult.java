package com.slotforge.api.payment;

public record PaymentIntentCreationResult(
        PaymentIntentResponse paymentIntent,
        boolean replayed
) {
}

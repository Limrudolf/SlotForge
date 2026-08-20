package com.slotforge.api.payment;

public class PaymentEventConflictException extends RuntimeException {

    public PaymentEventConflictException(String eventId) {
        super(
                "Payment event ID was already used for different callback data: "
                        + eventId
        );
    }
}

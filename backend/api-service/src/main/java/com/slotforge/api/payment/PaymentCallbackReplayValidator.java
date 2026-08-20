package com.slotforge.api.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class PaymentCallbackReplayValidator {

    public void requireMatchingReplay(
            PaymentEvent storedEvent,
            UUID expectedPaymentIntentId,
            PaymentEventType expectedEventType,
            String eventId
    ) {
        boolean sameIntent = storedEvent.getPaymentIntent().getId()
                .equals(expectedPaymentIntentId);
        boolean sameType = storedEvent.getEventType() == expectedEventType;
        if (!sameIntent || !sameType) {
            throw new PaymentEventConflictException(eventId);
        }
    }
}

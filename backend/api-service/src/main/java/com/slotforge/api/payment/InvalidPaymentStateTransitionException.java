package com.slotforge.api.payment;

import java.util.UUID;

public class InvalidPaymentStateTransitionException
        extends RuntimeException {

    public InvalidPaymentStateTransitionException(
            UUID paymentIntentId,
            PaymentIntentStatus from,
            PaymentIntentStatus to
    ) {
        super(
                "Payment intent " + paymentIntentId
                        + " cannot transition from " + from
                        + " to " + to
        );
    }
}

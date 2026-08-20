package com.slotforge.api.payment;

public class PaymentIntentUnavailableException extends RuntimeException {

    public PaymentIntentUnavailableException(String message) {
        super(message);
    }
}

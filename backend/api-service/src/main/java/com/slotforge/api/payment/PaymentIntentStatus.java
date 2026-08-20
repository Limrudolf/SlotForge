package com.slotforge.api.payment;

public enum PaymentIntentStatus {
    PENDING,
    AUTHORIZED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this != PENDING;
    }
}

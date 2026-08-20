package com.slotforge.api.booking;

public enum BookingStatus {
    PENDING_PAYMENT,
    PAYMENT_AUTHORIZED,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminalPaymentState() {
        return switch (this) {
            case CONFIRMED, PAYMENT_FAILED, CANCELLED, EXPIRED -> true;
            case PENDING_PAYMENT, PAYMENT_AUTHORIZED -> false;
        };
    }
}

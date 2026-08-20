package com.slotforge.api.booking;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("The idempotency key was already used for a different request");
    }
}

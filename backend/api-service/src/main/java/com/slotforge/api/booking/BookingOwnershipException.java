package com.slotforge.api.booking;

public class BookingOwnershipException extends RuntimeException {

    public BookingOwnershipException() {
        super("You are not allowed to access this booking");
    }
}

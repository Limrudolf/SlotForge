package com.slotforge.api.booking;

public record BookingCreationResult(
        BookingResponse booking,
        boolean replayed
) {
}

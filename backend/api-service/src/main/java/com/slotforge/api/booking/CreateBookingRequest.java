package com.slotforge.api.booking;

import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(
        @Positive(message = "Quantity must be positive")
        int quantity
) {
}

package com.slotforge.api.availability;

import java.util.UUID;

public record AvailabilityResponse(
        UUID sessionId,
        int totalCapacity,
        int remainingCapacity
) {

    public static AvailabilityResponse from(BookingSlot bookingSlot) {
        return new AvailabilityResponse(
                bookingSlot.getEventSession().getId(),
                bookingSlot.getTotalCapacity(),
                bookingSlot.getRemainingCapacity()
        );
    }
}
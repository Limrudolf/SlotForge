package com.slotforge.api.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID userId,
        UUID sessionId,
        BookingStatus status,
        int quantity,
        Instant createdAt,
        Instant updatedAt
) {

    public static BookingResponse from(Booking booking, int quantity) {
        return new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getEventSession().getId(),
                booking.getStatus(),
                quantity,
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}

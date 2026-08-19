package com.slotforge.api.event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String description,
        EventStatus status,
        UUID organizerId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getStatus(),
                event.getOrganizer().getId(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getVersion()
        );
    }
}

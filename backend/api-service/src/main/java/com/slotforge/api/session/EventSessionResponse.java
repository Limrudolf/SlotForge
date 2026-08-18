package com.slotforge.api.session;

import java.time.Instant;
import java.util.UUID;

public record EventSessionResponse(
        UUID id,
        UUID eventId,
        UUID venueId,
        Instant startTimeUtc,
        Instant endTimeUtc,
        String displayTimezone,
        EventSessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static EventSessionResponse from(EventSession session) {
        return new EventSessionResponse(
                session.getId(),
                session.getEvent().getId(),
                session.getVenue().getId(),
                session.getStartTimeUtc(),
                session.getEndTimeUtc(),
                session.getDisplayTimezone(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getVersion()
        );
    }
}
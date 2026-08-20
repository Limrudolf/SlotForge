package com.slotforge.api.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingStateTransitionResponse(
        UUID id,
        BookingStatus fromState,
        BookingStatus toState,
        UUID changedByUserId,
        String reason,
        Instant occurredAt
) {

    public static BookingStateTransitionResponse from(
            BookingStateTransition transition
    ) {
        return new BookingStateTransitionResponse(
                transition.getId(),
                transition.getFromState(),
                transition.getToState(),
                transition.getChangedByUser() == null
                        ? null
                        : transition.getChangedByUser().getId(),
                transition.getReason(),
                transition.getOccurredAt()
        );
    }
}

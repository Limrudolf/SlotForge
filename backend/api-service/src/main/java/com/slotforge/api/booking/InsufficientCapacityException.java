package com.slotforge.api.booking;

import java.util.UUID;

public class InsufficientCapacityException extends RuntimeException {

    public InsufficientCapacityException(
            UUID sessionId,
            int requestedQuantity,
            int remainingCapacity
    ) {
        super(
                "Session %s has insufficient capacity: requested %d, remaining %d"
                        .formatted(
                                sessionId,
                                requestedQuantity,
                                remainingCapacity
                        )
        );
    }
}

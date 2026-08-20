package com.slotforge.api.booking;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStateTransitionRepository
        extends JpaRepository<BookingStateTransition, UUID> {

    List<BookingStateTransition> findAllByBooking_IdOrderByOccurredAtAscIdAsc(
            UUID bookingId
    );
}

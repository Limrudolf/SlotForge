package com.slotforge.api.availability;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSlotRepository
        extends JpaRepository<BookingSlot, UUID> {

    Optional<BookingSlot> findByEventSession_Id(UUID eventSessionId);
}